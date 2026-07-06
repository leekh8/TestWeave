package com.testweave.service;

import com.testweave.domain.ScanResult;
import com.testweave.domain.SecurityTarget;
import com.testweave.repository.ScanResultRepository;
import com.testweave.repository.SecurityTargetRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** baseline 계산(latestStatusByKey)의 복합키·최신선택 검증 — 실제 DB(H2) 사용. */
@DataJpaTest
class ScanServiceBaselineTest {

    @Autowired
    SecurityTargetRepository targetRepo;
    @Autowired
    ScanResultRepository resultRepo;

    private ScanService service() {
        return new ScanService(targetRepo, resultRepo, List.of());
    }

    private SecurityTarget seedTarget() {
        return targetRepo.save(new SecurityTarget("t", "https://example.com", "HEADER,COOKIE"));
    }

    private void seed(SecurityTarget t, String type, String rule, String status, LocalDateTime at) {
        ScanResult r = new ScanResult(t, type, rule, status, "");
        r.setScannedAt(at);
        resultRepo.save(r);
    }

    @Test
    void sameRuleDifferentCheckType_doNotCollide() {
        SecurityTarget t = seedTarget();
        LocalDateTime at = LocalDateTime.now();
        // HeaderCheck·CookieCheck가 연결 실패 시 같은 rule명 "HTTP 연결"을 냄 — rule-only 키면 충돌
        seed(t, "HEADER", "HTTP 연결", "FAIL", at);
        seed(t, "COOKIE", "HTTP 연결", "PASS", at);

        Map<String, String> baseline = service().latestStatusByKey(t.getId());

        assertEquals("FAIL", baseline.get("HEADER|HTTP 연결"));
        assertEquals("PASS", baseline.get("COOKIE|HTTP 연결"));
        assertEquals(2, baseline.size(), "복합키로 충돌 없이 둘 다 보존");
    }

    @Test
    void latestScanWins() {
        SecurityTarget t = seedTarget();
        seed(t, "HEADER", "HSTS 적용", "FAIL", LocalDateTime.now().minusDays(1));
        seed(t, "HEADER", "HSTS 적용", "PASS", LocalDateTime.now());

        assertEquals("PASS", service().latestStatusByKey(t.getId()).get("HEADER|HSTS 적용"));
    }

    @Test
    void sameTimestamp_higherIdWins() {
        SecurityTarget t = seedTarget();
        LocalDateTime at = LocalDateTime.now();
        seed(t, "HEADER", "CSP 적용", "FAIL", at);   // 낮은 id
        seed(t, "HEADER", "CSP 적용", "PASS", at);   // 높은 id = 더 최근으로 tie-break

        assertEquals("PASS", service().latestStatusByKey(t.getId()).get("HEADER|CSP 적용"));
    }
}
