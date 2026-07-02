package com.testweave.repository;

import com.testweave.domain.SecurityTarget;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SecurityTargetRepository extends JpaRepository<SecurityTarget, Long> {

    Optional<SecurityTarget> findByUrl(String url);
}
