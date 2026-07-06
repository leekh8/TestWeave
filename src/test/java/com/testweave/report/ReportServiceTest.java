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

    @Test
    void rowsSortedRegressionFirst() {
        TargetReport report = new TargetReport("t", "https://x.example", List.of(
                new Regression("HEADER", "RULE_SAME", "PASS", "PASS", "SAME"),
                new Regression("HEADER", "RULE_FIXED", "FAIL", "PASS", "FIXED"),
                new Regression("TLS", "RULE_REG", "PASS", "FAIL", "REGRESSION")
        ), null);

        String html = service.render(List.of(report), at);

        int reg = html.indexOf("RULE_REG");
        int fixed = html.indexOf("RULE_FIXED");
        int same = html.indexOf("RULE_SAME");
        assertTrue(reg < fixed && fixed < same, "REGRESSION → FIXED → SAME 순으로 정렬돼야 함");
    }

    @Test
    void targetsWithRegressionSortedToTop() {
        TargetReport clean = new TargetReport("CLEAN_TARGET", "https://a.example", List.of(
                new Regression("HEADER", "r", "PASS", "PASS", "SAME")), null);
        TargetReport regressed = new TargetReport("REG_TARGET", "https://b.example", List.of(
                new Regression("HEADER", "r2", "PASS", "FAIL", "REGRESSION")), null);

        String html = service.render(List.of(clean, regressed), at);

        assertTrue(html.indexOf("REG_TARGET") < html.indexOf("CLEAN_TARGET"),
                "회귀 보유 대상이 위로 와야 함");
    }

    @Test
    void showsAllClearBannerWhenClean() {
        TargetReport clean = new TargetReport("t", "https://a.example", List.of(
                new Regression("HEADER", "r", "PASS", "PASS", "SAME")), null);

        String html = service.render(List.of(clean), at);

        assertTrue(html.contains("정상 — 회귀"), "전체 정상 배너 표시");
    }

    @Test
    void showsEmptyPlaceholderForNoRules() {
        TargetReport empty = new TargetReport("t", "https://a.example", List.of(), null);

        String html = service.render(List.of(empty), at);

        assertTrue(html.contains("점검 결과 없음"), "규칙 없는 대상은 placeholder 행 표시");
    }
}
