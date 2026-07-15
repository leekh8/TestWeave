package com.testweave.controller;

import com.testweave.exception.TargetNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/** 검증·조회 실패를 5xx가 아닌 정확한 4xx로 매핑한다. */
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** @Valid 실패 → 400 + 필드별 사유. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> onValidation(MethodArgumentNotValidException e) {
        Map<String, String> fields = e.getBindingResult().getFieldErrors().stream()
                .collect(Collectors.toMap(FieldError::getField, this::messageOf, (a, b) -> a));
        return body(HttpStatus.BAD_REQUEST, "요청 검증 실패", fields);
    }

    /** URL 스킴/형식 불량 등 → 400. */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> onBadRequest(IllegalArgumentException e) {
        return body(HttpStatus.BAD_REQUEST, e.getMessage(), null);
    }

    /** 대상 없음 → 404. */
    @ExceptionHandler(TargetNotFoundException.class)
    public ResponseEntity<Map<String, Object>> onNotFound(TargetNotFoundException e) {
        return body(HttpStatus.NOT_FOUND, e.getMessage(), null);
    }

    private String messageOf(FieldError f) {
        return f.getDefaultMessage() == null ? "유효하지 않은 값" : f.getDefaultMessage();
    }

    private ResponseEntity<Map<String, Object>> body(HttpStatus status, String message, Object detail) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("status", status.value());
        out.put("error", message);
        if (detail != null) {
            out.put("detail", detail);
        }
        return ResponseEntity.status(status).body(out);
    }
}
