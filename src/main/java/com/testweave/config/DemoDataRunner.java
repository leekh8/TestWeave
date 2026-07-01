package com.testweave.config;

import com.testweave.domain.SecurityTarget;
import com.testweave.repository.SecurityTargetRepository;
import com.testweave.scan.Regression;
import com.testweave.service.ScanService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 데모 실행기: --spring.profiles.active=demo 로 띄우면 샘플 대상을 등록하고
 * 즉시 스캔해 회귀 판정을 콘솔에 출력한다. (평상시에는 동작하지 않음)
 */
@Component
@Profile("demo")
public class DemoDataRunner implements CommandLineRunner {

    private final SecurityTargetRepository targetRepo;
    private final ScanService scanService;

    public DemoDataRunner(SecurityTargetRepository targetRepo, ScanService scanService) {
        this.targetRepo = targetRepo;
        this.scanService = scanService;
    }

    @Override
    public void run(String... args) {
        SecurityTarget target = targetRepo.save(
                new SecurityTarget("example.com", "https://example.com", "HEADER,COOKIE,TLS"));

        System.out.println("\n=== TestWeave 데모 스캔: " + target.getUrl() + " ===");
        List<Regression> regressions = scanService.scan(target.getId());
        for (Regression r : regressions) {
            System.out.printf("  [%-10s] %-45s %s%n", r.verdict(), r.rule(), r.current());
        }
        System.out.println("=== 완료 (" + regressions.size() + "개 규칙) ===\n");
    }
}
