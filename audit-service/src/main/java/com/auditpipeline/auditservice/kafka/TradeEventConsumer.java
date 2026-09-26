package com.auditpipeline.auditservice.kafka;

import com.auditpipeline.auditservice.dto.ComplianceAlert;
import com.auditpipeline.auditservice.dto.TradeEventPayload;
import com.auditpipeline.auditservice.entity.AuditResult;
import com.auditpipeline.auditservice.service.AuditProcessingService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
@Slf4j
public class TradeEventConsumer {

    private final AuditProcessingService auditProcessingService;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @Value("${app.kafka.compliance-alerts-topic}")
    private String complianceAlertsTopic;

    @KafkaListener(topics = "${app.kafka.trade-events-topic}", groupId = "${spring.kafka.consumer.group-id}")
    public void onTradeEvent(ConsumerRecord<String, String> record) throws Exception {
        String tradeIdKey = record.key();
        TradeEventPayload trade = objectMapper.readValue(record.value(), TradeEventPayload.class);

        Optional<AuditResult> result = auditProcessingService.process(tradeIdKey, trade);
        if (result.isEmpty()) {
            return; // duplicate delivery, already handled
        }

        AuditResult auditResult = result.get();
        if (!auditResult.isFlagged()) {
            return;
        }

        List<String> reasons = Arrays.asList(auditResult.getReasons().split(","));
        ComplianceAlert alert = new ComplianceAlert(
                tradeIdKey,
                trade.accountId(),
                trade.counterparty(),
                trade.countryCode(),
                trade.notionalAmount(),
                trade.currency(),
                reasons,
                Instant.now()
        );

        String alertJson = objectMapper.writeValueAsString(alert);
        kafkaTemplate.send(complianceAlertsTopic, tradeIdKey, alertJson);
        log.info("Published compliance alert for trade {}: {}", tradeIdKey, reasons);
    }
}
