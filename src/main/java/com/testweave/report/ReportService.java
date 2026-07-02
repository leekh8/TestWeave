package com.testweave.report;

import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
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
        Context ctx = new Context(Locale.KOREAN);
        ctx.setVariable("reports", reports);
        ctx.setVariable("generatedAt", generatedAt.format(TS));
        ctx.setVariable("totalRules", reports.stream().mapToLong(r -> r.regressions().size()).sum());
        ctx.setVariable("totalRegressions", total(reports, "REGRESSION"));
        ctx.setVariable("totalFixed", total(reports, "FIXED"));
        ctx.setVariable("totalFails", reports.stream().mapToLong(TargetReport::countFail).sum());
        ctx.setVariable("totalErrors", reports.stream().filter(TargetReport::hasError).count());
        return engine.process("report", ctx);
    }

    private static long total(List<TargetReport> reports, String verdict) {
        return reports.stream().mapToLong(r -> r.countVerdict(verdict)).sum();
    }
}
