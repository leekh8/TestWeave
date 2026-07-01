package com.testweave.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** 회귀 판정(verdict) 순수 로직 단위 테스트 — 네트워크/DB 불필요. */
class ScanServiceVerdictTest {

    @Test
    void newRule_whenNoBaseline() {
        assertEquals("NEW", ScanService.verdict(null, "PASS"));
        assertEquals("NEW", ScanService.verdict(null, "FAIL"));
    }

    @Test
    void regression_whenPassToFail() {
        assertEquals("REGRESSION", ScanService.verdict("PASS", "FAIL"));
    }

    @Test
    void fixed_whenFailToPass() {
        assertEquals("FIXED", ScanService.verdict("FAIL", "PASS"));
    }

    @Test
    void same_whenUnchanged() {
        assertEquals("SAME", ScanService.verdict("PASS", "PASS"));
        assertEquals("SAME", ScanService.verdict("FAIL", "FAIL"));
    }
}
