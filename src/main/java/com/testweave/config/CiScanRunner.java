package com.testweave.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.testweave.domain.SecurityTarget;
import com.testweave.report.ReportService;
import com.testweave.report.TargetReport;
import com.testweave.repository.SecurityTargetRepository;
import com.testweave.scan.Regression;
import com.testweave.service.ScanService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * CI 실행기: --spring.profiles.active=ci 로 띄우면 서버 없이
 * 설정된 대상들을 스캔하고 HTML 리포트 + summary.json 을 파일로 남긴 뒤 종료한다.
 * baseline은 H2 파일 DB(data/)에 누적 — CI에서는 actions/cache 로 실행 간 유지한다.
 */
@Component
@Profile("ci")
public class CiScanRunner implements CommandLineRunner {

    private final SecurityTargetRepository targetRepo;
    private final ScanService scanService;
    private final ReportService reportService;

    @Value("${testweave.ci.targets}")
    private String targets;          // 콤마 구분 URL 목록

    @Value("${testweave.ci.check-types:HEADER,COOKIE,TLS}")
    private String checkTypes;

    @Value("${testweave.ci.report-dir:reports}")
    private String reportDir;

    public CiScanRunner(SecurityTargetRepository targetRepo,
                        ScanService scanService,
                        ReportService reportService) {
        this.targetRepo = targetRepo;
        this.scanService = scanService;
        this.reportService = reportService;
    }

    @Override
    public void run(String... args) throws Exception {
        List<String> urls = Arrays.stream(targets.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();

        List<TargetReport> reports = new ArrayList<>();
        for (String url : urls) {
            try {
                SecurityTarget target = targetRepo.findByUrl(url)
                        .orElseGet(() -> targetRepo.save(new SecurityTarget(hostOf(url), url, checkTypes)));
                List<Regression> regressions = scanService.scan(target.getId());
                reports.add(new TargetReport(target.getName(), url, regressions, null));
            } catch (Exception e) {
                // 대상 하나가 죽어도 나머지는 계속 — 오류는 리포트/summary에 표면화
                reports.add(new TargetReport(hostOf(url), url, List.of(), e.toString()));
            }
        }

        LocalDateTime now = LocalDateTime.now();
        Path dir = Path.of(reportDir);
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("scan-report.html"),
                reportService.render(reports, now), StandardCharsets.UTF_8);

        long totalRules = reports.stream().mapToLong(r -> r.regressions().size()).sum();
        long regressions = reports.stream().mapToLong(r -> r.countVerdict("REGRESSION")).sum();
        long fixed = reports.stream().mapToLong(r -> r.countVerdict("FIXED")).sum();
        long fails = reports.stream().mapToLong(TargetReport::countFail).sum();
        long errors = reports.stream().filter(TargetReport::hasError).count();

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("generatedAt", now.toString());
        summary.put("targets", urls.size());
        summary.put("rules", totalRules);
        summary.put("regressions", regressions);
        summary.put("fixed", fixed);
        summary.put("fails", fails);
        summary.put("errors", errors);
        Files.writeString(dir.resolve("summary.json"),
                new ObjectMapper().writeValueAsString(summary), StandardCharsets.UTF_8);

        System.out.printf("%n=== TestWeave CI 스캔 완료: 대상 %d / 규칙 %d ===%n", urls.size(), totalRules);
        System.out.printf("  REGRESSION=%d  FIXED=%d  FAIL=%d  ERROR=%d%n", regressions, fixed, fails, errors);
        System.out.println("  리포트: " + dir.resolve("scan-report.html").toAbsolutePath());
    }

    private static String hostOf(String url) {
        try {
            String host = URI.create(url).getHost();
            return host != null ? host : url;
        } catch (Exception e) {
            return url;
        }
    }
}
