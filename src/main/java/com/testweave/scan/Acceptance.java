package com.testweave.scan;

import java.time.LocalDate;

/**
 * 미충족(FAIL) 상태 하나에 대한 위험 수용 판단.
 *
 * <p>수용은 "못 고쳤다"가 아니라 "알고 있고, 이유가 있고, 언제 다시 볼지 정했다"는 기록이다.
 * 그래서 사유와 기한이 없는 수용은 존재할 수 없다. 둘 중 하나라도 비면 등록부 파싱이 거부한다.
 */
public record Acceptance(LocalDate until, String reason, State state, long daysLeft) {

    public enum State {
        /** 기한 내. 이 행은 미충족 집계에서 빠진다. */
        ACTIVE,
        /** 기한이 지났다. 미충족으로 되돌아가며, 왜 되돌아왔는지 따로 표시된다. */
        EXPIRED,
        /** 회귀나 소멸이라 수용이 무효다. 등록부에 있어도 적용하지 않는다. */
        REJECTED
    }

    public boolean active() {
        return state == State.ACTIVE;
    }

    public boolean expired() {
        return state == State.EXPIRED;
    }

    public boolean rejected() {
        return state == State.REJECTED;
    }

    /** 기한이 코앞인 수용. 만료되고 나서 알면 이미 늦다. */
    public boolean dueSoon() {
        return active() && daysLeft <= 14;
    }
}
