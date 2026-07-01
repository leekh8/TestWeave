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

    public ScanResult(SecurityTarget target, String checkType, String rule, String status, String detail) {
        this.target = target;
        this.checkType = checkType;
        this.rule = rule;
        this.status = status;
        this.detail = detail;
    }
}
