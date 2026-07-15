package com.testweave.check;

/** 대상 URL 또는 리다이렉트 목적지가 SSRF 가드에 차단됨. */
public class SsrfBlockedException extends Exception {

    public SsrfBlockedException(String message) {
        super(message);
    }
}
