package com.testweave.report;

import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

import com.testweave.scan.Regression;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * 스캔 결과를 HTML 리포트로 렌더링한다.
 * 웹 컨텍스트와 무관하게 동작해야 하므로(ci 프로파일은 서버를 띄우지 않음)
 * Spring MVC 통합 대신 Thymeleaf 엔진을 직접 구성한다.
 */
@Service
public class ReportService {

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final TemplateEngine engine;

    public ReportService() {
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setSuffix(".html");
        resolver.setTemplateMode(TemplateMode.HTML);
        resolver.setCharacterEncoding("UTF-8");
        this.engine = new TemplateEngine();
        this.engine.setTemplateResolver(resolver);
    }

    public String render(List<TargetReport> reports, LocalDateTime generatedAt) {
        // 가장 중요한 것(회귀 → 오류 → 실패)이 맨 위로 오도록 정렬해서 렌더한다.
        // 정렬은 표시 순서일 뿐이므로 KPI 합계는 순서와 무관하게 동일하다.
        List<TargetReport> sorted = reports.stream()
                .map(ReportService::sortRows)
                .sorted(Comparator.comparingInt(ReportService::targetRank))
                .toList();

        Context ctx = new Context(Locale.KOREAN);
        ctx.setVariable("reports", sorted);
        ctx.setVariable("generatedAt", generatedAt.format(TS));
        ctx.setVariable("totalRules", sorted.stream().mapToLong(r -> r.regressions().size()).sum());
        ctx.setVariable("totalRegressions", total(sorted, "REGRESSION"));
        ctx.setVariable("totalFixed", total(sorted, "FIXED"));
        ctx.setVariable("totalFails", sorted.stream().mapToLong(TargetReport::countFail).sum());
        ctx.setVariable("totalErrors", sorted.stream().filter(TargetReport::hasError).count());
        ctx.setVariable("totalMissing", sorted.stream().mapToLong(TargetReport::countMissing).sum());
        return engine.process("report", ctx);
    }

    private static long total(List<TargetReport> reports, String verdict) {
        return reports.stream().mapToLong(r -> r.countVerdict(verdict)).sum();
    }

    /** 회귀 보유 → 규칙 소멸 → 스캔 오류 → 실패 보유 → 나머지 순. */
    private static int targetRank(TargetReport t) {
        if (t.countVerdict("REGRESSION") > 0) return 0;
        if (t.countMissing() > 0) return 1;   // 대상이 통째로 안 잡힌 신호
        if (t.hasError()) return 2;
        if (t.countFail() > 0) return 3;
        return 4;
    }

    /** 대상 내 판정 행을 REGRESSION → MISSING → FAIL → NEW → FIXED → SAME 순으로 정렬한 새 리포트. */
    private static TargetReport sortRows(TargetReport t) {
        if (t.hasError() || t.regressions().isEmpty()) return t;
        List<Regression> rows = t.regressions().stream()
                .sorted(Comparator.comparingInt(ReportService::rowRank))
                .toList();
        return new TargetReport(t.name(), t.url(), rows, t.error());
    }

    private static int rowRank(Regression r) {
        if ("REGRESSION".equals(r.verdict())) return 0;
        if ("MISSING".equals(r.verdict())) return 1; // 직전엔 쟀는데 이번엔 못 잰 규칙
        if ("FAIL".equals(r.current())) return 2;    // 회귀는 아니나 현재 미충족
        if ("NEW".equals(r.verdict())) return 3;
        if ("FIXED".equals(r.verdict())) return 4;
        return 5;                                    // SAME / PASS
    }
}
