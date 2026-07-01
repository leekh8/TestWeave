package com.testweave.service;

import com.testweave.check.CheckOutcome;
import com.testweave.check.SecurityCheck;
import com.testweave.domain.ScanResult;
import com.testweave.domain.SecurityTarget;
import com.testweave.repository.ScanResultRepository;
import com.testweave.repository.SecurityTargetRepository;
import com.testweave.scan.Regression;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** 대상을 스캔하고, 직전 결과 대비 회귀를 계산해 새 결과를 저장한다. */
@Service
public class ScanService {

    private final SecurityTargetRepository targetRepo;
    private final ScanResultRepository resultRepo;
    private final Map<String, SecurityCheck> checks;

    public ScanService(SecurityTargetRepository targetRepo,
                       ScanResultRepository resultRepo,
                       List<SecurityCheck> checkList) {
        this.targetRepo = targetRepo;
        this.resultRepo = resultRepo;
        this.checks = checkList.stream()
                .collect(Collectors.toMap(SecurityCheck::type, Function.identity()));
    }

    @Transactional
    public List<Regression> scan(Long targetId) {
        SecurityTarget target = targetRepo.findById(targetId)
                .orElseThrow(() -> new IllegalArgumentException("대상 없음: " + targetId));

        // 직전 스캔의 규칙별 상태 = baseline (새 결과 저장 전에 먼저 읽는다)
        Map<String, String> baseline = latestStatusByRule(targetId);

        List<Regression> regressions = new ArrayList<>();
        for (String rawType : target.getCheckTypes().split(",")) {
            SecurityCheck check = checks.get(rawType.trim());
            if (check == null) {
                continue; // 알 수 없는 모듈은 건너뜀
            }
            for (CheckOutcome o : check.run(target)) {
                resultRepo.save(new ScanResult(target, check.type(), o.rule(), o.status(), o.detail()));
                String prev = baseline.get(o.rule());
                regressions.add(new Regression(check.type(), o.rule(), prev, o.status(), verdict(prev, o.status())));
            }
        }
        return regressions;
    }

    private Map<String, String> latestStatusByRule(Long targetId) {
        Map<String, String> latest = new HashMap<>();
        // 최신순 정렬이므로 각 rule의 첫 등장(=가장 최근) 상태만 취한다
        for (ScanResult r : resultRepo.findByTargetIdOrderByScannedAtDesc(targetId)) {
            latest.putIfAbsent(r.getRule(), r.getStatus());
        }
        return latest;
    }

    /** 회귀 판정 (순수 함수 — 단위 테스트 대상). */
    static String verdict(String previous, String current) {
        if (previous == null) {
            return "NEW";
        }
        if ("PASS".equals(previous) && "FAIL".equals(current)) {
            return "REGRESSION";
        }
        if ("FAIL".equals(previous) && "PASS".equals(current)) {
            return "FIXED";
        }
        return "SAME";
    }
}
