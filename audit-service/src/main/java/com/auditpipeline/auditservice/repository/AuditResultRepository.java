package com.auditpipeline.auditservice.repository;

import com.auditpipeline.auditservice.entity.AuditResult;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.UUID;

public interface AuditResultRepository extends JpaRepository<AuditResult, UUID> {

    long countByAccountIdAndProcessedAtAfter(String accountId, Instant since);
}
