package com.testweave.scan;

/**
 * 이번 스캔 결과를 직전(baseline) 결과와 비교한 회귀 판정.
 * verdict: NEW(신규) / REGRESSION(PASS→FAIL, 후퇴) / FIXED(FAIL→PASS, 개선) / SAME(동일).
 */
public record Regression(
        String checkType,
        String rule,
        String previous,   // 직전 상태 (없으면 null)
        String current,    // 이번 상태
        String verdict) {
}
