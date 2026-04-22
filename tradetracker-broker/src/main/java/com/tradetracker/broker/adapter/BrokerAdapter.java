package com.tradetracker.broker.adapter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public interface BrokerAdapter {

    String brokerId();

    List<BrokerTrade> fetchTrades(String accessToken, LocalDate since);

    Map<String, BigDecimal> fetchCashBalances(String accessToken);

    record BrokerTrade(
        String externalRef,
        String ticker,
        String exchange,
        String tradeType,
        BigDecimal quantity,
        BigDecimal price,
        BigDecimal fees,
        String currency,
        LocalDate tradeDate,
        LocalDate settlementDate,
        String rawPayloadJson
    ) {}
}