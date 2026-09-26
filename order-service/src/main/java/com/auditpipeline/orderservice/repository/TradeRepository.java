package com.auditpipeline.orderservice.repository;

import com.auditpipeline.orderservice.entity.Trade;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface TradeRepository extends JpaRepository<Trade, UUID> {
}
