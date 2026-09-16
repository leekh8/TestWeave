package com.testweave.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/** 한 번의 스캔에서 규칙(rule) 하나에 대한 판정 결과. */
@Entity
@Getter
@Setter
@NoArgsConstructor
public class ScanResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    private SecurityTarget target;

    private String checkType;    // HEADER / COOKIE / TLS
    private String rule;         // "HSTS 적용" 등 사람이 읽는 규칙명
    private String status;       // PASS / FAIL

    @Column(length = 500)
    private String detail;       // 실패 사유 등

    private LocalDateTime scannedAt = LocalDateTime.now();

    /**
     * 한 번의 스캔을 묶는 식별자. 규칙 소멸(MISSING)을 "직전 스캔에 있었는가"로 판정하려면
     * 어느 행들이 같은 회차인지 알아야 한다. 타임스탬프는 행마다 밀리초가 달라 묶이지 않는다.
     *
     * <p>이 컬럼이 생기기 전 행은 null이다. 그 행들은 직전 회차가 될 수 없으므로 자연히
     * 판정에서 빠진다. 폐기된 옛 규칙명이 영원히 소멸로 잡히던 문제가 이걸로 끝난다.
     */
    private String scanId;

    public ScanResult(SecurityTarget target, String checkType, String rule, String status, String detail) {
        this.target = target;
        this.checkType = checkType;
        this.rule = rule;
        this.status = status;
        this.detail = detail;
    }
}
