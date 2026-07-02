package com.testweave.report;

import com.testweave.scan.Regression;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** HTML 리포트 렌더링 검증 — 네트워크/DB/Spring 컨텍스트 불필요. */
class ReportServiceTest {

    private final ReportService service = new ReportService();
    private final LocalDateTime at = LocalDateTime.of(2026, 7, 2, 6, 20);

    @Test
    void rendersRegressionHighlighted() {
        TargetReport report = new TargetReport("blog", "https://leekh8.github.io", List.of(
                new Regression("HEADER", "HSTS 적용", "PASS", "FAIL", "REGRESSION"),
                new Regression("HEADER", "CSP 적용", "FAIL", "PASS", "FIXED"),
                new Regression("TLS", "HTTPS 사용", null, "PASS", "NEW")
        ), null);

        String html = service.render(List.of(report), at);

        assertTrue(html.contains("REGRESSION"));
        assertTrue(html.contains("HSTS 적용"));
        assertTrue(html.contains("https://leekh8.github.io"));
        assertTrue(html.contains("2026-07-02 06:20:00"));
        assertTrue(html.contains("class=\"badge REGRESSION\""), "REGRESSION 배지 강조");
    }

    @Test
    void escapesHtmlInRuleNames() {
        TargetReport report = new TargetReport("t", "https://x.example", List.of(
                new Regression("HEADER", "<script>alert(1)</script>", null, "FAIL", "NEW")
        ), null);

        String html = service.render(List.of(report), at);

        assertFalse(html.contains("<script>alert(1)</script>"), "규칙명은 이스케이프되어야 함");
        assertTrue(html.contains("&lt;script&gt;"));
    }

    @Test
    void rendersScanErrorBlock() {
        TargetReport report = new TargetReport("dead", "https://dead.example", List.of(),
                "java.net.UnknownHostException: dead.example");

        String html = service.render(List.of(report), at);

        assertTrue(html.contains("스캔 실패"));
        assertTrue(html.contains("UnknownHostException"));
    }
}
