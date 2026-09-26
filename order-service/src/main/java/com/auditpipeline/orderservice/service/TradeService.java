package com.auditpipeline.orderservice.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.auditpipeline.orderservice.dto.TradeRequest;
import com.auditpipeline.orderservice.entity.OutboxEvent;
import com.auditpipeline.orderservice.entity.Trade;
import com.auditpipeline.orderservice.repository.OutboxEventRepository;
import com.auditpipeline.orderservice.repository.TradeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class TradeService {

    private final TradeRepository tradeRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    /**
     * The core of the transactional outbox pattern: the trade row and the
     * outbox row are written by the SAME method, inside the SAME Spring
     * @Transactional boundary, which becomes one Postgres transaction.
     *
     * Either both rows commit together, or (on any failure) neither does.
     * There is no separate call to Kafka here at all -- Debezium is
     * responsible for turning the outbox_events row into a Kafka message,
     * asynchronously, by tailing the Postgres WAL. That decoupling is the
     * entire point: this method can never "half fail" the way a
     * save-to-database-then-call-Kafka approach could.
     */
    @Transactional
    public Trade recordTrade(TradeRequest request) {
        Trade trade = Trade.builder()
                .accountId(request.accountId())
                .counterparty(request.counterparty())
                .countryCode(request.countryCode())
                .tradeType(request.tradeType())
                .notionalAmount(request.notionalAmount())
                .currency(request.currency())
                .build();

        Trade savedTrade = tradeRepository.save(trade);

        OutboxEvent outboxEvent = OutboxEvent.builder()
                .aggregateType("Trade")
                .aggregateId(savedTrade.getId().toString())
                .eventType("TradeExecuted")
                .payload(toJson(savedTrade))
                .build();

        outboxEventRepository.save(outboxEvent);

        log.info("Recorded trade {} and outbox event in a single transaction", savedTrade.getId());

        return savedTrade;
    }

    private String toJson(Trade trade) {
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("tradeId", trade.getId().toString());
            payload.put("accountId", trade.getAccountId());
            payload.put("counterparty", trade.getCounterparty());
            payload.put("countryCode", trade.getCountryCode());
            payload.put("tradeType", trade.getTradeType());
            payload.put("notionalAmount", trade.getNotionalAmount());
            payload.put("currency", trade.getCurrency());
            payload.put("status", trade.getStatus());
            payload.put("createdAt", trade.getCreatedAt().toString());
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            // A serialization failure here should fail the whole transaction --
            // we never want a trade committed without its outbox event.
            throw new IllegalStateException("Failed to serialize trade outbox payload", e);
        }
    }
}
