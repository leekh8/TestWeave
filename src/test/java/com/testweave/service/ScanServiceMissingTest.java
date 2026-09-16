package com.testweave.service;

import com.testweave.check.CheckOutcome;
import com.testweave.check.SecurityCheck;
import com.testweave.domain.ScanResult;
import com.testweave.domain.SecurityTarget;
import com.testweave.repository.ScanResultRepository;
import com.testweave.repository.SecurityTargetRepository;
import com.testweave.scan.Regression;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 규칙 소멸(MISSING) 판정 검증.
 *
 * 이 테스트가 지키는 것: 대상이 죽어서 규칙이 결과에서 사라지는 상황을 게이트가 잡는가.
 * 이전에는 이번 스캔 결과만 순회했기 때문에 사라진 규칙이 판정에서 통째로 빠졌고,
 * REGRESSION 0 / ERROR 0으로 CI가 초록불을 냈다.
 */
@DataJpaTest
class ScanServiceMissingTest {

    @Autowired
    SecurityTargetRepository targetRepo;
    @Autowired
    ScanResultRepository resultRepo;

    /** 반환할 outcome을 테스트가 직접 정하는 스텁. */
    private record StubCheck(String type, List<CheckOutcome> outcomes) implements SecurityCheck {
        @Override
        public List<CheckOutcome> run(SecurityTarget target) {
            return outcomes;
        }
    }

    private ScanService service(SecurityCheck... checks) {
        return new ScanService(targetRepo, resultRepo, List.of(checks));
    }

    private SecurityTarget seedTarget() {
        return targetRepo.save(new SecurityTarget("t", "https://example.com", "HEADER"));
    }

    private void seedBaseline(SecurityTarget t, String rule, String status) {
        ScanResult r = new ScanResult(t, "HEADER", rule, status, "");
        r.setScannedAt(LocalDateTime.now().minusDays(1));
        resultRepo.save(r);
    }

    private Map<String, Regression> byRule(List<Regression> rs) {
        return rs.stream().collect(Collectors.toMap(Regression::rule, Function.identity()));
    }

    @Test
    void 대상이_죽어_규칙이_사라지면_MISSING으로_잡는다() {
        SecurityTarget t = seedTarget();
        // 어제까지 헤더 3개를 재고 있었다
        seedBaseline(t, "HSTS 적용", "PASS");
        seedBaseline(t, "CSP 적용", "PASS");
        seedBaseline(t, "X-Frame-Options 적용", "FAIL");

        // 오늘은 사이트가 죽어 연결 실패 한 행만 나온다 (HeaderCheck의 실제 동작)
        SecurityCheck dead = new StubCheck("HEADER",
                List.of(CheckOutcome.fail("HTTP 연결", "요청 실패: connect timed out")));

        Map<String, Regression> got = byRule(service(dead).scan(t.getId()));

        assertEquals(4, got.size(), "연결 실패 1행 + 사라진 규칙 3행");
        assertEquals("MISSING", got.get("HSTS 적용").verdict());
        assertEquals("MISSING", got.get("CSP 적용").verdict());
        assertEquals("MISSING", got.get("X-Frame-Options 적용").verdict());
        assertEquals("PASS", got.get("HSTS 적용").previous(), "직전 상태는 보존해야 원인 추적이 된다");
        assertEquals("NEW", got.get("HTTP 연결").verdict());
    }

    @Test
    void 규칙이_그대로면_MISSING은_없다() {
        SecurityTarget t = seedTarget();
        seedBaseline(t, "HSTS 적용", "PASS");

        SecurityCheck alive = new StubCheck("HEADER", List.of(CheckOutcome.pass("HSTS 적용")));

        List<Regression> got = service(alive).scan(t.getId());

        assertEquals(1, got.size());
        assertEquals("SAME", got.get(0).verdict());
    }

    @Test
    void PASS에서_사라진_것과_PASS에서_FAIL로_간_것은_다른_판정이다() {
        SecurityTarget t = seedTarget();
        seedBaseline(t, "HSTS 적용", "PASS");
        seedBaseline(t, "CSP 적용", "PASS");

        // HSTS는 FAIL로 후퇴, CSP는 아예 결과에서 사라짐
        SecurityCheck partial = new StubCheck("HEADER",
                List.of(CheckOutcome.fail("HSTS 적용", "max-age 부족")));

        Map<String, Regression> got = byRule(service(partial).scan(t.getId()));

        assertEquals("REGRESSION", got.get("HSTS 적용").verdict(), "후퇴는 조치 대상이 설정이다");
        assertEquals("MISSING", got.get("CSP 적용").verdict(), "소멸은 먼저 왜 못 쟀는지를 본다");
    }

    @Test
    void 첫_스캔이라_baseline이_없으면_MISSING은_생기지_않는다() {
        SecurityTarget t = seedTarget();

        SecurityCheck first = new StubCheck("HEADER", List.of(CheckOutcome.pass("HSTS 적용")));

        List<Regression> got = service(first).scan(t.getId());

        assertEquals(1, got.size());
        assertEquals("NEW", got.get(0).verdict());
        assertTrue(got.stream().noneMatch(r -> "MISSING".equals(r.verdict())));
    }
}
