package com.testweave.exception;

/** 요청한 점검 대상이 없음 → HTTP 404로 매핑된다. */
public class TargetNotFoundException extends RuntimeException {

    public TargetNotFoundException(Long id) {
        super("대상 없음: " + id);
    }
}
