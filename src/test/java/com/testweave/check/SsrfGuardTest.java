package com.testweave.check;

import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.net.UnknownHostException;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** SSRF 가드 — 내부/사설/메타데이터 대상 차단 검증. 리터럴 IP만 사용해 DNS/네트워크 불필요. */
class SsrfGuardTest {

    @Test
    void blocksLoopbackAndPrivateAndMetadata() {
        assertNotNull(SsrfGuard.blockReason("http://127.0.0.1/"), "루프백 차단");
        assertNotNull(SsrfGuard.blockReason("http://169.254.169.254/latest/meta-data/"), "메타데이터 차단");
        assertNotNull(SsrfGuard.blockReason("http://10.0.0.5"), "10/8 차단");
        assertNotNull(SsrfGuard.blockReason("http://192.168.1.1"), "192.168/16 차단");
        assertNotNull(SsrfGuard.blockReason("http://172.16.0.1"), "172.16/12 차단");
    }

    @Test
    void blocksBadScheme() {
        assertNotNull(SsrfGuard.blockReason("ftp://example.com"));
        assertNotNull(SsrfGuard.blockReason("file:///etc/passwd"));
    }

    @Test
    void allowsPublicAddresses() {
        // 공개 IP는 리터럴이라 DNS 없이 통과해야 한다
        assertNull(SsrfGuard.blockReason("http://8.8.8.8"));
        assertNull(SsrfGuard.blockReason("https://1.1.1.1"));
    }

    @Test
    void isBlockedAddress_classifiesRanges() throws UnknownHostException {
        assertTrue(SsrfGuard.isBlockedAddress(InetAddress.getByName("127.0.0.1")));
        assertTrue(SsrfGuard.isBlockedAddress(InetAddress.getByName("169.254.169.254")));
        assertTrue(SsrfGuard.isBlockedAddress(InetAddress.getByName("10.1.2.3")));
        assertFalse(SsrfGuard.isBlockedAddress(InetAddress.getByName("8.8.8.8")));
    }
}
