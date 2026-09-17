package com.auditpipeline.auditservice.service;

import com.auditpipeline.auditservice.dto.TradeEventPayload;
import com.auditpipeline.auditservice.repository.AuditResultRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Three deliberately simple, independently-explainable rules -- individually
 * unremarkable, but together they read as a real (if simplified) trade
 * surveillance rule set rather than a Kafka plumbing demo. See
 * PROGRESS.md / the build plan for swapping the sanctioned-country list for
 * a real OFAC SDN snapshot.
 */
@Component
public class RuleEngine {

    private final AuditResultRepository auditResultRepository;
    private final BigDecimal thresholdAmount;
    private final int velocityWindowMinutes;
    private final int velocityMaxTrades;
    private final Set<String> sanctionedCountries;

    public RuleEngine(
            AuditResultRepository auditResultRepository,
            @Value("${app.rules.threshold-amount}") BigDecimal thresholdAmount,
            @Value("${app.rules.velocity-window-minutes}") int velocityWindowMinutes,
            @Value("${app.rules.velocity-max-trades}") int velocityMaxTrades,
            @Value("${app.rules.sanctioned-countries}") String sanctionedCountriesCsv
    ) {
        this.auditResultRepository = auditResultRepository;
        this.thresholdAmount = thresholdAmount;
        this.velocityWindowMinutes = velocityWindowMinutes;
        this.velocityMaxTrades = velocityMaxTrades;
        this.sanctionedCountries = Arrays.stream(sanctionedCountriesCsv.split(","))
                .map(String::trim)
                .map(String::toUpperCase)
                .collect(Collectors.toSet());
    }

    /**
     * Returns the list of rule codes this trade tripped -- empty means clean.
     * Evaluated in order from cheapest to most expensive check.
     */
    public List<String> evaluate(TradeEventPayload trade) {
        List<String> reasons = new ArrayList<>();

        if (sanctionedCountries.contains(trade.countryCode().toUpperCase())) {
            reasons.add("SANCTIONED_COUNTERPARTY_COUNTRY");
        }

        if (trade.notionalAmount().compareTo(thresholdAmount) > 0) {
            reasons.add("THRESHOLD_BREACH");
        }

        Instant windowStart = Instant.now().minus(Duration.ofMinutes(velocityWindowMinutes));
        long recentTradeCount = auditResultRepository.countByAccountIdAndProcessedAtAfter(
                trade.accountId(), windowStart);
        // +1 to include the trade currently being evaluated, which hasn't
        // been saved yet at the point this runs.
        if (recentTradeCount + 1 >= velocityMaxTrades) {
            reasons.add("VELOCITY_BREACH");
        }

        return reasons;
    }
}
