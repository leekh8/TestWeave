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
import java.nio.file.StandardOpenOption;
import java.io.IOException;
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

    @Value("${testweave.ci.targets:}")
    private String targets;          // 콤마 구분 URL 목록

    /**
     * 대상 목록 파일 경로. 한 줄에 URL 하나, {@code #}으로 시작하면 주석.
     *
     * <p>목록을 properties에 두면 대상을 추가할 때마다 커밋이 필요한데, 이 저장소는
     * 공개라 업무 대상 URL을 올릴 수가 없다. 그래서 파일을 쓰고 그 파일은 gitignore에
     * 둔다. 저장소에는 {@code targets.sample.txt}만 남는다.
     *
     * <p>파일이 있으면 파일이 이긴다. 둘 다 없으면 대상 0건으로 즉시 실패한다.
     * 조용히 0건을 스캔하고 "이상 없음"을 내보내면 그게 제일 나쁘다.
     */
    @Value("${testweave.ci.targets-file:targets.local.txt}")
    private String targetsFile;

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
        List<String> urls = resolveTargets();
        if (urls.isEmpty()) {
            throw new IllegalStateException(
                    "스캔 대상이 없습니다. " + targetsFile + " 를 만들거나 "
                    + "testweave.ci.targets 를 지정하세요 (targets.sample.txt 참고).");
        }

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
        long missing = reports.stream().mapToLong(TargetReport::countMissing).sum();

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("generatedAt", now.toString());
        summary.put("targets", urls.size());
        summary.put("rules", totalRules);
        summary.put("regressions", regressions);
        summary.put("fixed", fixed);
        summary.put("fails", fails);
        summary.put("errors", errors);
        summary.put("missing", missing);
        Files.writeString(dir.resolve("summary.json"),
                new ObjectMapper().writeValueAsString(summary), StandardCharsets.UTF_8);

        System.out.printf("%n=== TestWeave CI 스캔 완료: 대상 %d / 규칙 %d ===%n", urls.size(), totalRules);
        System.out.printf("  REGRESSION=%d  MISSING=%d  FIXED=%d  FAIL=%d  ERROR=%d%n",
                regressions, missing, fixed, fails, errors);
        System.out.println("  리포트: " + dir.resolve("scan-report.html").toAbsolutePath());
        writeStepSummary(urls.size(), totalRules, regressions, missing, fixed, fails, errors);
    }

    /** 대상 목록. 파일이 있으면 파일, 없으면 properties. */
    private List<String> resolveTargets() throws IOException {
        Path file = Path.of(targetsFile);
        if (Files.exists(file)) {
            List<String> fromFile = Files.readAllLines(file, StandardCharsets.UTF_8).stream()
                    .map(String::trim)
                    .filter(s -> !s.isEmpty() && !s.startsWith("#"))
                    .toList();
            System.out.printf("대상 목록: %s (%d건)%n", file.toAbsolutePath(), fromFile.size());
            return fromFile;
        }
        return Arrays.stream(targets.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    /**
     * GitHub Actions 실행 페이지에 요약을 찍는다.
     *
     * <p>이게 없으면 결과를 보려고 Actions 탭에서 run을 열고 스크롤해서 아티팩트 zip을
     * 받아 풀어서 브라우저로 열어야 한다. 여섯 단계다. 매일 아침에 볼 수 있는 동선이
     * 아니라서 결국 아무도 안 본다.
     */
    private void writeStepSummary(long targets, long rules, long regressions,
                                  long missing, long fixed, long fails, long errors) {
        String path = System.getenv("GITHUB_STEP_SUMMARY");
        if (path == null || path.isBlank()) {
            // 로컬 실행. 콘솔 출력으로 충분하다. 다만 CI에서 이 줄이 찍히면
            // 환경변수가 하위 프로세스까지 안 내려온 것이므로 원인을 바로 안다.
            System.out.println("  요약: GITHUB_STEP_SUMMARY 미설정, 건너뜀");
            return;
        }
        try {
            Files.writeString(Path.of(path),
                    stepSummaryMarkdown(targets, rules, regressions, missing, fixed, fails, errors),
                    StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            System.out.println("  요약 기록: " + path);
        } catch (IOException e) {
            // 요약을 못 써도 스캔 결과는 이미 파일에 있다. 여기서 실패로 끌고 가지 않는다.
            System.err.println("STEP_SUMMARY 기록 실패: " + e.getMessage());
        }
    }

    /**
     * 요약 마크다운. 순수 함수라 테스트로 고정한다.
     *
     * <p>처음 쓸 때 표의 인자 순서를 헤더와 다르게 넣어 FAIL 칸에 FIXED 값이 찍혔다.
     * 숫자가 그럴듯해서 눈으로는 안 걸린다. 값을 서로 다르게 준 테스트만 잡는다.
     */
    static String stepSummaryMarkdown(long targets, long rules, long regressions,
                                      long missing, long fixed, long fails, long errors) {
        String verdict;
        if (regressions > 0 || missing > 0 || errors > 0) {
            verdict = "🛑 조치 필요";
        } else if (fails > 0) {
            // 회귀는 아니지만 계속 미충족인 상태. 게이트는 통과시키되 "이상 없음"이라고
            // 쓰지는 않는다. 8건이 깨진 채로 초록불을 보는 것이 무감각을 만든다.
            verdict = "⚠️ 미충족 " + fails + "건";
        } else {
            verdict = "✅ 이상 없음";
        }
        return """
                ## TestWeave %s

                | 대상 | 규칙 | REGRESSION | MISSING | FAIL | FIXED | 오류 |
                |---:|---:|---:|---:|---:|---:|---:|
                | %d | %d | %d | %d | %d | %d | %d |

                REGRESSION = 직전 PASS에서 이번 FAIL. MISSING = 직전엔 쟀는데 이번 결과에 없음(대상 불통 신호).
                자세한 내용은 아래 `scan-report` 아티팩트.
                """.formatted(verdict, targets, rules, regressions, missing, fails, fixed, errors);
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
