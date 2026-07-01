package com.testweave.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/** 보안 점검 대상(웹/API 엔드포인트). */
@Entity
@Getter
@Setter
@NoArgsConstructor
public class SecurityTarget {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;         // 사람이 알아볼 이름 (예: "회사 홈")
    private String url;          // https://example.com
    private String checkTypes;   // 실행할 모듈, 콤마 구분 (예: "HEADER,COOKIE,TLS")
    private LocalDateTime createdAt = LocalDateTime.now();

    public SecurityTarget(String name, String url, String checkTypes) {
        this.name = name;
        this.url = url;
        this.checkTypes = checkTypes;
    }
}
