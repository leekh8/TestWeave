package com.testweave.repository;

import com.testweave.domain.ScanResult;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ScanResultRepository extends JpaRepository<ScanResult, Long> {

    /** 특정 대상의 결과를 최신순으로 반환 (baseline 계산용). */
    List<ScanResult> findByTargetIdOrderByScannedAtDesc(Long targetId);
}
