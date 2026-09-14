package com.vijayprasanna.orderservice.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.math.BigDecimal;

public record TradeRequest(

        @NotBlank(message = "accountId is required")
        String accountId,

        @NotBlank(message = "counterparty is required")
        String counterparty,

        @NotBlank(message = "countryCode is required")
        @Pattern(regexp = "^[A-Z]{2}$", message = "countryCode must be a 2-letter ISO code, e.g. US")
        String countryCode,

        @NotBlank(message = "tradeType is required")
        @Pattern(regexp = "^(BUY|SELL)$", message = "tradeType must be BUY or SELL")
        String tradeType,

        @NotNull(message = "notionalAmount is required")
        @DecimalMin(value = "0.01", message = "notionalAmount must be positive")
        BigDecimal notionalAmount,

        @NotBlank(message = "currency is required")
        @Pattern(regexp = "^[A-Z]{3}$", message = "currency must be a 3-letter ISO code, e.g. USD")
        String currency
) {
}
