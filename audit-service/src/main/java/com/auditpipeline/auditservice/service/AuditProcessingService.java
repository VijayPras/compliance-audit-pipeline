package com.auditpipeline.auditservice.service;

import com.auditpipeline.auditservice.dto.TradeEventPayload;
import com.auditpipeline.auditservice.entity.AuditResult;
import com.auditpipeline.auditservice.entity.InboxEvent;
import com.auditpipeline.auditservice.repository.AuditResultRepository;
import com.auditpipeline.auditservice.repository.InboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuditProcessingService {

    private final InboxEventRepository inboxEventRepository;
    private final AuditResultRepository auditResultRepository;
    private final RuleEngine ruleEngine;

    /**
     * Returns the AuditResult if this trade was newly processed, or empty if
     * it was a duplicate delivery already handled before (the Inbox Pattern
     * from the architecture doc -- Kafka consumers must tolerate at-least-once
     * delivery, and this is what makes reprocessing the same message safe).
     */
    @Transactional
    public Optional<AuditResult> process(String tradeIdKey, TradeEventPayload trade) {
        UUID eventId = UUID.fromString(tradeIdKey);

        if (inboxEventRepository.existsById(eventId)) {
            log.info("Dropping duplicate delivery for trade {}", eventId);
            return Optional.empty();
        }

        List<String> reasons = ruleEngine.evaluate(trade);

        AuditResult result = AuditResult.builder()
                .tradeId(eventId)
                .accountId(trade.accountId())
                .counterparty(trade.counterparty())
                .countryCode(trade.countryCode())
                .notionalAmount(trade.notionalAmount())
                .currency(trade.currency())
                .flagged(!reasons.isEmpty())
                .reasons(reasons.isEmpty() ? null : String.join(",", reasons))
                .build();

        auditResultRepository.save(result);
        inboxEventRepository.save(new InboxEvent(eventId, Instant.now()));

        log.info("Processed trade {} -- flagged={} reasons={}", eventId, result.isFlagged(), reasons);

        return Optional.of(result);
    }
}
