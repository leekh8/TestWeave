package com.testweave.check;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** HSTS max-age 값 검증 + 존재하지만-약한 헤더 검증 — 취약 설정 통과 회귀 방지. */
class HeaderCheckTest {

    @Test
    void maxAgeZero_isDisabled() {
        assertFalse(HeaderCheck.hstsEnabled("max-age=0"));
        assertFalse(HeaderCheck.hstsEnabled("max-age=0; includeSubDomains"));
    }

    @Test
    void positiveMaxAge_isEnabled() {
        assertTrue(HeaderCheck.hstsEnabled("max-age=31536000"));
        assertTrue(HeaderCheck.hstsEnabled("max-age=31536000; includeSubDomains; preload"));
        assertTrue(HeaderCheck.hstsEnabled("max-age = 63072000"));  // 공백 허용
    }

    @Test
    void missingMaxAge_notEnabled() {
        assertFalse(HeaderCheck.hstsEnabled("includeSubDomains"));
        assertFalse(HeaderCheck.hstsEnabled(""));
    }

    @Test
    void shortMaxAge_isTooWeak() {
        // 1년 미만은 실효성이 없어 비활성으로 본다(존재만으로 PASS 금지)
        assertFalse(HeaderCheck.hstsEnabled("max-age=1"));
        assertFalse(HeaderCheck.hstsEnabled("max-age=86400"));           // 1일
        assertTrue(HeaderCheck.hstsEnabled("max-age=31536000"));         // 정확히 1년
    }

    @Test
    void xFrameOptions_weakValuesFail() {
        assertNull(HeaderCheck.headerWeakness("X-Frame-Options", "DENY"));
        assertNull(HeaderCheck.headerWeakness("X-Frame-Options", "sameorigin"));
        assertNotNull(HeaderCheck.headerWeakness("X-Frame-Options", "ALLOWALL"));
        assertNotNull(HeaderCheck.headerWeakness("X-Frame-Options", "ALLOW-FROM https://x"));
    }

    @Test
    void csp_wildcardAndUnsafeInlineFail() {
        assertNull(HeaderCheck.headerWeakness("Content-Security-Policy", "default-src 'self'"));
        assertNotNull(HeaderCheck.headerWeakness("Content-Security-Policy", "default-src 'self' 'unsafe-inline'"));
        assertNotNull(HeaderCheck.headerWeakness("Content-Security-Policy", "default-src *"));
    }

    @Test
    void xContentTypeOptions_mustBeNosniff() {
        assertNull(HeaderCheck.headerWeakness("X-Content-Type-Options", "nosniff"));
        assertNotNull(HeaderCheck.headerWeakness("X-Content-Type-Options", "sniff"));
    }

    @Test
    void referrerPolicy_unknownTokenFails() {
        assertNull(HeaderCheck.headerWeakness("Referrer-Policy", "no-referrer"));
        assertNull(HeaderCheck.headerWeakness("Referrer-Policy", "strict-origin-when-cross-origin"));
        assertNull(HeaderCheck.headerWeakness("Referrer-Policy", "no-referrer, strict-origin")); // 목록 중 유효
        assertNotNull(HeaderCheck.headerWeakness("Referrer-Policy", "banana"));
    }
}
