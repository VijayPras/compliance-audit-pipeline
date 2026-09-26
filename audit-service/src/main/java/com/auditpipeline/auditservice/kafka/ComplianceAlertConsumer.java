package com.auditpipeline.auditservice.kafka;

import com.auditpipeline.auditservice.dto.ComplianceAlert;
import com.auditpipeline.auditservice.sse.AlertBroadcaster;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Deliberately a separate consumer from TradeEventConsumer, reading back the
 * topic this same service just published to. This keeps the SSE-facing path
 * decoupled from trade processing -- in a real deployment, other consumers
 * (a notification service, a separate dashboard instance) could subscribe to
 * compliance-alerts.v1 the same way, without touching trade processing at
 * all.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ComplianceAlertConsumer {

    private final AlertBroadcaster alertBroadcaster;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "${app.kafka.compliance-alerts-topic}", groupId = "${spring.kafka.consumer.group-id}-sse")
    public void onComplianceAlert(String alertJson) throws Exception {
        ComplianceAlert alert = objectMapper.readValue(alertJson, ComplianceAlert.class);
        alertBroadcaster.broadcast(alert);
    }
}
