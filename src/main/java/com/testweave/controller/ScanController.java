package com.testweave.controller;

import com.testweave.domain.SecurityTarget;
import com.testweave.exception.TargetNotFoundException;
import com.testweave.report.ReportService;
import com.testweave.report.TargetReport;
import com.testweave.repository.SecurityTargetRepository;
import com.testweave.scan.Regression;
import com.testweave.service.ScanService;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/targets")
public class ScanController {

    private final SecurityTargetRepository targetRepo;
    private final ScanService scanService;
    private final ReportService reportService;

    public ScanController(SecurityTargetRepository targetRepo, ScanService scanService,
                          ReportService reportService) {
        this.targetRepo = targetRepo;
        this.scanService = scanService;
        this.reportService = reportService;
    }

    /** 등록된 점검 대상 목록. */
    @GetMapping
    public List<SecurityTarget> list() {
        return targetRepo.findAll();
    }

    /** 점검 대상 등록. body 예: {"name":"홈","url":"https://example.com","checkTypes":"HEADER,COOKIE,TLS"} */
    @PostMapping
    public SecurityTarget register(@Valid @RequestBody TargetRequest req) {
        validateUrl(req.url());
        String name = (req.name() == null || req.name().isBlank()) ? hostOf(req.url()) : req.name().trim();
        return targetRepo.save(new SecurityTarget(name, req.url().trim(), req.checkTypes().trim()));
    }

    /** 등록 단계에서 스킴/형식이 잘못된 URL을 거른다(내부주소 차단은 스캔 시 SsrfGuard가 수행). */
    private static void validateUrl(String url) {
        URI uri;
        try {
            uri = URI.create(url.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("잘못된 URL 형식: " + url);
        }
        String scheme = uri.getScheme();
        if (scheme == null || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) {
            throw new IllegalArgumentException("http/https URL만 등록 가능: " + url);
        }
        if (uri.getHost() == null || uri.getHost().isBlank()) {
            throw new IllegalArgumentException("호스트가 없는 URL: " + url);
        }
    }

    private static String hostOf(String url) {
        try {
            String host = URI.create(url.trim()).getHost();
            return host != null ? host : url.trim();
        } catch (Exception e) {
            return url.trim();
        }
    }

    /** 대상을 스캔하고 직전 대비 회귀 판정 목록을 반환. */
    @PostMapping("/{id}/scan")
    public List<Regression> scan(@PathVariable Long id) {
        return scanService.scan(id);
    }

    /** 대상을 스캔하고 결과를 HTML 리포트로 반환 (브라우저 확인용). */
    @PostMapping(value = "/{id}/report", produces = MediaType.TEXT_HTML_VALUE)
    public String scanReport(@PathVariable Long id) {
        SecurityTarget target = targetRepo.findById(id)
                .orElseThrow(() -> new TargetNotFoundException(id));
        List<Regression> regressions = scanService.scan(id);
        return reportService.render(
                List.of(new TargetReport(target.getName(), target.getUrl(), regressions, null)),
                LocalDateTime.now());
    }
}
