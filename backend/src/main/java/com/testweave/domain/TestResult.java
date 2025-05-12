// Entity class
package com.testweave.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@NoArgsConstructor
public class TestResult {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    private TestCase testCase;

    private String status; // PASS or FAIL
    private long executionTime;
    private LocalDateTime executedAt = LocalDateTime.now();

    @Column(length = 2000)
    private String errorMessage; // 실패 시 저장할 에러 메시지

}
