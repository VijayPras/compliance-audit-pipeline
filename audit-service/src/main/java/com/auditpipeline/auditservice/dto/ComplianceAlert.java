package com.auditpipeline.auditservice.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Published to compliance-alerts.v1 when a trade trips one or more rules,
 * and what the SSE stream sends on to the dashboard.
 */
public record ComplianceAlert(
        String tradeId,
        String accountId,
        String counterparty,
        String countryCode,
        BigDecimal notionalAmount,
        String currency,
        List<String> reasons,
        Instant flaggedAt
) {
}
