package com.testweave.scan;

/**
 * 이번 스캔 결과를 직전(baseline) 결과와 비교한 회귀 판정.
 * verdict: NEW(신규) / REGRESSION(PASS에서 FAIL, 후퇴) / FIXED(FAIL에서 PASS, 개선) / SAME(동일)
 * / MISSING(직전엔 쟀는데 이번 결과에 없음).
 *
 * <p>acceptance는 위험 수용 등록부 적용 결과다. null이면 등록부에 없는 항목이다.
 * 판정(verdict)과 분리해 두는 이유는, 수용이 판정을 바꾸는 게 아니라 <b>판정을 그대로 둔 채
 * 조치 여부만 유보</b>하는 것이기 때문이다. FAIL은 수용해도 여전히 FAIL이다.
 */
public record Regression(
        String checkType,
        String rule,
        String previous,   // 직전 상태 (없으면 null)
        String current,    // 이번 상태
        String verdict,
        Acceptance acceptance) {

    public Regression(String checkType, String rule, String previous, String current, String verdict) {
        this(checkType, rule, previous, current, verdict, null);
    }

    public Regression withAcceptance(Acceptance a) {
        return new Regression(checkType, rule, previous, current, verdict, a);
    }

    /** 기한 내 수용. 이 행만 미충족 집계에서 빠진다. */
    public boolean accepted() {
        return acceptance != null && acceptance.active();
    }

    /** 수용해 뒀는데 기한이 지났다. 미충족으로 되돌아온 상태. */
    public boolean acceptanceExpired() {
        return acceptance != null && acceptance.expired();
    }

    /** 등록부에 있으나 회귀나 소멸이라 적용하지 않은 상태. */
    public boolean acceptanceRejected() {
        return acceptance != null && acceptance.rejected();
    }

    /** 조치가 필요한 미충족. 수용 중인 것은 빠지고, 기한이 지난 것은 다시 들어온다. */
    public boolean actionableFail() {
        return "FAIL".equals(current) && !accepted();
    }
}
