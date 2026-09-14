package com.auditpipeline.orderservice.dto;

import com.auditpipeline.orderservice.entity.Trade;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record TradeResponse(
        UUID id,
        String accountId,
        String counterparty,
        String countryCode,
        String tradeType,
        BigDecimal notionalAmount,
        String currency,
        String status,
        Instant createdAt
) {
    public static TradeResponse from(Trade trade) {
        return new TradeResponse(
                trade.getId(),
                trade.getAccountId(),
                trade.getCounterparty(),
                trade.getCountryCode(),
                trade.getTradeType(),
                trade.getNotionalAmount(),
                trade.getCurrency(),
                trade.getStatus(),
                trade.getCreatedAt()
        );
    }
}
