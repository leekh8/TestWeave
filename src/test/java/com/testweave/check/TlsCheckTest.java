package com.testweave.check;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** TLS 프로토콜 버전 판정 — TLS 1.0/1.1이 PASS로 통과하는 회귀 방지. 네트워크 불필요. */
class TlsCheckTest {

    @Test
    void modernVersions_areSecure() {
        assertTrue(TlsCheck.isSecureProtocol("TLSv1.2"));
        assertTrue(TlsCheck.isSecureProtocol("TLSv1.3"));
    }

    @Test
    void legacyVersions_areInsecure() {
        assertFalse(TlsCheck.isSecureProtocol("TLSv1"));
        assertFalse(TlsCheck.isSecureProtocol("TLSv1.1"));
        assertFalse(TlsCheck.isSecureProtocol("SSLv3"));
        assertFalse(TlsCheck.isSecureProtocol(null));
    }
}
