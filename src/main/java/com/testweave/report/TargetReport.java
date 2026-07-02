package com.testweave.report;

import com.testweave.scan.Regression;

import java.util.List;

/**
 * 리포트 렌더링 단위 — 대상 하나의 스캔 결과 묶음.
 * error가 있으면 스캔 자체가 실패한 대상(네트워크 오류 등)이다.
 */
public record TargetReport(String name, String url, List<Regression> regressions, String error) {

    public boolean hasError() {
        return error != null;
    }

    public long countVerdict(String verdict) {
        return regressions.stream().filter(r -> verdict.equals(r.verdict())).count();
    }

    public long countFail() {
        return regressions.stream().filter(r -> "FAIL".equals(r.current())).count();
    }
}
