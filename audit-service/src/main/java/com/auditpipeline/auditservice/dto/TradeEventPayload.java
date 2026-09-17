package com.auditpipeline.auditservice.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;

/**
 * Mirrors the JSON payload order-service's TradeService writes into
 * outbox_events.payload -- this is exactly what arrives as the Kafka
 * message value on trade-events.v1, since Debezium's outbox EventRouter
 * republishes that payload verbatim as the record value.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TradeEventPayload(
        String tradeId,
        String accountId,
        String counterparty,
        String countryCode,
        String tradeType,
        BigDecimal notionalAmount,
        String currency,
        String status,
        String createdAt
) {
}
