package com.testweave.check;

import com.testweave.domain.SecurityTarget;

import java.util.List;

/**
 * 보안 점검 모듈 인터페이스. 새 점검을 추가하려면 이 인터페이스를 구현한
 * 스프링 빈(@Component)을 하나 더 만들면 ScanService가 자동으로 인식한다.
 */
public interface SecurityCheck {

    /** 모듈 식별자 (SecurityTarget.checkTypes와 매칭). 예: "HEADER". */
    String type();

    /** 대상을 점검하고 규칙별 결과 목록을 반환한다. */
    List<CheckOutcome> run(SecurityTarget target);
}
