package com.testweave.check;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** HSTS max-age 값 검증 — 헤더가 있어도 max-age=0이면 비활성이라는 회귀 방지. */
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
}
