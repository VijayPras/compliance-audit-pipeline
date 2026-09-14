package com.vijayprasanna.orderservice.repository;

import com.vijayprasanna.orderservice.entity.Trade;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface TradeRepository extends JpaRepository<Trade, UUID> {
}
