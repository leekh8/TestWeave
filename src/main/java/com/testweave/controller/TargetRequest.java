package com.testweave.controller;

import jakarta.validation.constraints.NotBlank;

/**
 * 대상 등록 요청 본문. 검증 경계를 엔티티가 아닌 DTO에 두어,
 * {@code url}/{@code checkTypes} 누락을 저장 이전에 400으로 반려한다.
 * (누락 시 스캔 단계의 {@code getCheckTypes().split(",")}가 NPE → 500이 되던 문제 차단)
 */
public record TargetRequest(
        String name,
        @NotBlank(message = "url은 필수입니다") String url,
        @NotBlank(message = "checkTypes는 필수입니다 (예: HEADER,COOKIE,TLS)") String checkTypes) {
}
