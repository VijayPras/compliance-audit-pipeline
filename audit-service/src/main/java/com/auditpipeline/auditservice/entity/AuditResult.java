package com.auditpipeline.auditservice.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "audit_results")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditResult {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "trade_id", nullable = false)
    private UUID tradeId;

    @Column(name = "account_id", nullable = false)
    private String accountId;

    @Column(name = "counterparty", nullable = false)
    private String counterparty;

    @Column(name = "country_code", nullable = false)
    private String countryCode;

    @Column(name = "notional_amount", nullable = false)
    private BigDecimal notionalAmount;

    @Column(name = "currency", nullable = false)
    private String currency;

    @Column(name = "flagged", nullable = false)
    private boolean flagged;

    @Column(name = "reasons")
    private String reasons;

    @Column(name = "processed_at", nullable = false)
    @Builder.Default
    private Instant processedAt = Instant.now();
}
