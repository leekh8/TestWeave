package com.testweave.check;

import org.junit.jupiter.api.Test;

import java.net.InetAddress;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 사설 대역 완화 스위치의 경계 검증.
 *
 * 스위치를 켜는 순간 이 스캐너는 내부망에 요청을 대신 보내 주는 통로가 된다.
 * 그래서 "열어도 끝까지 닫혀 있어야 하는 것"이 무엇인지가 이 기능의 본체다.
 */
class ScanPolicyTest {

    private static InetAddress ip(String s) throws Exception {
        return InetAddress.getByName(s);
    }

    @Test
    void 기본값은_사설_대역_전면_차단이다() throws Exception {
        assertTrue(SsrfGuard.isBlockedAddress(ip("127.0.0.1")));
        assertTrue(SsrfGuard.isBlockedAddress(ip("10.0.0.5")));
        assertTrue(SsrfGuard.isBlockedAddress(ip("172.16.0.1")));
        assertTrue(SsrfGuard.isBlockedAddress(ip("192.168.1.1")));
    }

    @Test
    void 완화하면_사내_자산을_볼_수_있다() throws Exception {
        assertFalse(SsrfGuard.isBlockedAddress(ip("10.0.0.5"), true));
        assertFalse(SsrfGuard.isBlockedAddress(ip("192.168.1.1"), true));
        assertFalse(SsrfGuard.isBlockedAddress(ip("127.0.0.1"), true));
    }

    @Test
    void 완화해도_클라우드_메타데이터는_막힌다() throws Exception {
        // 이 기능에서 가장 중요한 한 줄. 169.254.169.254는 내부 자산 점검과 아무 상관이
        // 없으면서 인스턴스 자격증명을 흘린다. 사설 대역을 열어 준 것이 여기까지
        // 열어 준 것으로 번지면 스위치 자체가 취약점이 된다.
        assertTrue(SsrfGuard.isBlockedAddress(ip("169.254.169.254"), true));
        assertTrue(SsrfGuard.isBlockedAddress(ip("169.254.0.1"), true));
    }

    @Test
    void 완화해도_와일드카드와_멀티캐스트는_막힌다() throws Exception {
        assertTrue(SsrfGuard.isBlockedAddress(ip("0.0.0.0"), true));
        assertTrue(SsrfGuard.isBlockedAddress(ip("224.0.0.1"), true));
    }

    @Test
    void 공개_주소는_양쪽_모두_통과한다() throws Exception {
        assertFalse(SsrfGuard.isBlockedAddress(ip("93.184.216.34")));      // example.com
        assertFalse(SsrfGuard.isBlockedAddress(ip("93.184.216.34"), true));
    }

    @Test
    void 스킴_검사는_완화와_무관하다() {
        assertTrue(SsrfGuard.blockReason("file:///etc/passwd", true).contains("스킴"));
        assertTrue(SsrfGuard.blockReason("gopher://x/", true).contains("스킴"));
    }
}
