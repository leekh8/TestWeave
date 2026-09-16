package com.testweave.service;

import com.testweave.check.CheckOutcome;
import com.testweave.check.SecurityCheck;
import com.testweave.domain.ScanResult;
import com.testweave.domain.SecurityTarget;
import com.testweave.exception.TargetNotFoundException;
import com.testweave.repository.ScanResultRepository;
import com.testweave.repository.SecurityTargetRepository;
import com.testweave.scan.Regression;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
                .orElseThrow(() -> new TargetNotFoundException(targetId));

        // 직전 스캔의 (checkType,rule)별 상태 = baseline (새 결과 저장 전에 먼저 읽는다)
        Map<String, String> baseline = latestStatusByKey(targetId);

        List<Regression> regressions = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (String rawType : target.getCheckTypes().split(",")) {
            SecurityCheck check = checks.get(rawType.trim());
            if (check == null) {
                continue; // 알 수 없는 모듈은 건너뜀
            }
            for (CheckOutcome o : check.run(target)) {
                resultRepo.save(new ScanResult(target, check.type(), o.rule(), o.status(), o.detail()));
                String k = key(check.type(), o.rule());
                seen.add(k);
                String prev = baseline.get(k);
                regressions.add(new Regression(check.type(), o.rule(), prev, o.status(), verdict(prev, o.status())));
            }
        }

        // baseline에 있었는데 이번 결과에 없는 규칙 = 규칙 소멸.
        //
        // 이번 outcome만 순회하면 이 경우가 판정에서 통째로 빠진다. 그런데 가장 심각한 사고가
        // 정확히 이 모양으로 나타난다. 대상이 다운되면 HeaderCheck가 예외를 "HTTP 연결" FAIL
        // 한 행으로 흡수하므로, 원래 있던 헤더 규칙 5개가 결과에서 사라지고 새 규칙 하나가
        // NEW로 붙는다. REGRESSION은 0이고 예외도 안 새서 CI가 초록불로 통과한다.
        // 즉 "전부 정상"과 "사이트가 죽었다"가 같은 모양으로 보인다.
        //
        // 소멸을 REGRESSION에 합치지 않고 MISSING으로 따로 두는 이유: PASS→FAIL(후퇴)과
        // 규칙이 사라진 것(관측 불가)은 조치가 다르다. 전자는 설정을 되돌리는 문제고
        // 후자는 먼저 왜 못 쟀는지를 봐야 한다.
        for (Map.Entry<String, String> e : baseline.entrySet()) {
            if (seen.contains(e.getKey())) {
                continue;
            }
            String[] parts = e.getKey().split("\\|", 2);
            String checkType = parts[0];
            String rule = parts.length > 1 ? parts[1] : "";
            regressions.add(new Regression(checkType, rule, e.getValue(), MISSING, MISSING));
        }
        return regressions;
    }

    /** 직전엔 있었는데 이번 스캔에서 사라진 규칙. current 상태이자 verdict로 함께 쓴다. */
    public static final String MISSING = "MISSING";

    /**
     * baseline 키 = checkType|rule 복합키.
     * rule명만으로 키를 잡으면 서로 다른 체크가 같은 rule명(예: HeaderCheck·CookieCheck의
     * 연결 실패 "HTTP 연결")을 낼 때 충돌해 회귀 판정·집계가 뒤섞인다.
     */
    private static String key(String checkType, String rule) {
        return checkType + "|" + rule;
    }

    Map<String, String> latestStatusByKey(Long targetId) {
        Map<String, String> latest = new HashMap<>();
        // 최신순(동시각이면 id 내림차순) 정렬이므로 각 키의 첫 등장(=가장 최근) 상태만 취한다
        for (ScanResult r : resultRepo.findByTargetIdOrderByScannedAtDescIdDesc(targetId)) {
            latest.putIfAbsent(key(r.getCheckType(), r.getRule()), r.getStatus());
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
