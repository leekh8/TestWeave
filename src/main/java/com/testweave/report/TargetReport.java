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

    /**
     * 조치가 필요한 미충족 수. 기한 내 수용은 빠지고, 기한이 지난 수용은 다시 들어온다.
     *
     * <p>수용분을 여기서 빼는 것이 이 기능의 전부다. 고칠 방법이 없는 항목이 매일 같은
     * 숫자로 경고에 남으면 사람은 경고 자체를 안 보게 되고, 그때 진짜 회귀가 같은 화면에
     * 섞여 들어온다.
     */
    public long countFail() {
        return regressions.stream().filter(Regression::actionableFail).count();
    }

    /** 기한 내 수용 중인 미충족 수. 0이 아니면 리포트에 사유와 남은 기간이 함께 나온다. */
    public long countAccepted() {
        return regressions.stream().filter(Regression::accepted).count();
    }

    /** 기한이 지나 미충족으로 되돌아온 수. 조치 기한을 넘겼다는 신호다. */
    public long countExpired() {
        return regressions.stream().filter(Regression::acceptanceExpired).count();
    }

    /** 기한이 코앞인 수용. 만료되고 나서 아는 것보다 미리 아는 편이 낫다. */
    public long countDueSoon() {
        return regressions.stream().filter(r -> r.acceptance() != null && r.acceptance().dueSoon()).count();
    }

    /** 수용해 뒀는데 회귀나 소멸이 나서 수용이 무효가 된 수. */
    public long countAcceptanceRejected() {
        return regressions.stream().filter(Regression::acceptanceRejected).count();
    }

    /** 직전엔 있었는데 이번 스캔에서 사라진 규칙 수. 대상 불통 신호라 게이트가 이 값을 본다. */
    public long countMissing() {
        return countVerdict("MISSING");
    }
}
