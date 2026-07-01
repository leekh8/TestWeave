package com.testweave.check;

/** 규칙 하나에 대한 점검 결과(불변). 각 SecurityCheck가 여러 개를 반환한다. */
public record CheckOutcome(String rule, String status, String detail) {

    public static CheckOutcome pass(String rule) {
        return new CheckOutcome(rule, "PASS", "");
    }

    public static CheckOutcome fail(String rule, String detail) {
        return new CheckOutcome(rule, "FAIL", detail);
    }
}
