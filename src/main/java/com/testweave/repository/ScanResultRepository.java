package com.testweave.repository;

import com.testweave.domain.ScanResult;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ScanResultRepository extends JpaRepository<ScanResult, Long> {

    /** 특정 대상의 결과를 최신순으로 반환 (baseline 계산용).
     *  scannedAt이 같은 스캔 내 동시각일 때 id 내림차순으로 tie-break해 baseline 선택을 결정론적으로. */
    List<ScanResult> findByTargetIdOrderByScannedAtDescIdDesc(Long targetId);
}
