package com.testweave.check;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

/**
 * 스캔 범위 정책.
 *
 * <p>기본값은 사설 대역 전면 차단이다. 공개 인터넷 자산만 본다는 뜻이고, 그래서 이
 * 스캐너를 남이 실행해도 남의 내부망을 찌를 수 없다.
 *
 * <p>사내 자산을 점검하려면 {@code testweave.allow-private=true}로 연다. 여는 순간
 * 이 프로세스는 내부망에 요청을 대신 보내 주는 통로가 되므로, 자기 자산을 아는
 * 사람이 자기 환경에서 켜는 스위치여야 한다. 공개 CI에서는 켜지 않는다.
 * 링크로컬(클라우드 메타데이터)은 켜도 막힌다. {@link SsrfGuard} 참고.
 */
@Component
public class ScanPolicy {

    @Value("${testweave.allow-private:false}")
    private boolean allowPrivate;

    public boolean allowPrivate() {
        return allowPrivate;
    }

    /**
     * 켜져 있으면 기동 로그에 남긴다. 설정 파일 한 줄은 잊히지만 실행할 때마다 뜨는
     * 경고는 잊히지 않는다. 완화 상태로 돌고 있다는 것을 모르는 것이 가장 나쁘다.
     */
    @PostConstruct
    void warnIfRelaxed() {
        if (allowPrivate) {
            System.out.println("[TestWeave] 경고: 사설 대역 점검이 허용된 상태입니다"
                    + " (testweave.allow-private=true). 내부 자산을 스캔할 수 있습니다."
                    + " 공개 환경에서는 끄십시오.");
        }
    }
}
