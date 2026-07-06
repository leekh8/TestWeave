package com.testweave.check;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Set-Cookie 속성 파싱 검증 — 부분문자열 오탐 회귀 방지. 네트워크 불필요. */
class CookieCheckTest {

    @Test
    void valueContainingSecureWord_isNotFalsePositive() {
        // 값에 "secure"/"httponly" 문자열이 들어있지만 실제 속성은 없음
        Set<String> attrs = CookieCheck.attributesOf("sid=SecureRandomHttpOnlyValue123; Path=/");
        assertFalse(attrs.contains("secure"), "값 속 'secure'를 속성으로 오판하면 안 됨");
        assertFalse(attrs.contains("httponly"));
        assertFalse(attrs.contains("samesite"));
    }

    @Test
    void allFlagsPresent() {
        Set<String> attrs = CookieCheck.attributesOf("sid=abc; Secure; HttpOnly; SameSite=Strict; Path=/");
        assertTrue(attrs.contains("secure"));
        assertTrue(attrs.contains("httponly"));
        assertTrue(attrs.contains("samesite"));
    }

    @Test
    void caseInsensitive() {
        Set<String> attrs = CookieCheck.attributesOf("sid=abc; SECURE; httpOnly");
        assertTrue(attrs.contains("secure"));
        assertTrue(attrs.contains("httponly"));
    }

    @Test
    void noAttributes() {
        assertTrue(CookieCheck.attributesOf("sid=value").isEmpty());
    }
}
