package com.auditpipeline.orderservice.entity;

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
@Table(name = "trades")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Trade {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "account_id", nullable = false)
    private String accountId;

    @Column(name = "counterparty", nullable = false)
    private String counterparty;

    @Column(name = "country_code", nullable = false)
    private String countryCode;

    @Column(name = "trade_type", nullable = false)
    private String tradeType;

    @Column(name = "notional_amount", nullable = false)
    private BigDecimal notionalAmount;

    @Column(name = "currency", nullable = false)
    private String currency;

    @Column(name = "status", nullable = false)
    @Builder.Default
    private String status = "EXECUTED";

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();
}
