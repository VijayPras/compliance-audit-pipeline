package com.auditpipeline.orderservice.controller;

import com.auditpipeline.orderservice.dto.TradeRequest;
import com.auditpipeline.orderservice.dto.TradeResponse;
import com.auditpipeline.orderservice.entity.Trade;
import com.auditpipeline.orderservice.service.TradeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/trades")
@RequiredArgsConstructor
public class TradeController {

    private final TradeService tradeService;

    @PostMapping
    public ResponseEntity<TradeResponse> submitTrade(@Valid @RequestBody TradeRequest request) {
        Trade trade = tradeService.recordTrade(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(TradeResponse.from(trade));
    }
}
