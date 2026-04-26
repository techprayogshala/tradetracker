package com.tradetracker.portfolio.repository;

import com.tradetracker.portfolio.entity.TradeEvent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TradeEventRepository extends JpaRepository<TradeEvent, UUID> {

    @Query("""
            SELECT t FROM TradeEvent t
            JOIN FETCH t.security s
            WHERE t.portfolio.id = :portfolioId
              AND (:ticker IS NULL OR s.ticker = :ticker)
              AND (:type IS NULL OR t.tradeType = :type)
              AND (:fromDate IS NULL OR t.tradeDate >= :fromDate)
              AND (:toDate IS NULL OR t.tradeDate <= :toDate)
            """)
    Page<TradeEvent> findFiltered(
            @Param("portfolioId") UUID portfolioId,
            @Param("ticker") String ticker,
            @Param("type") TradeEvent.TradeType tradeType,
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate,
            Pageable pageable
    );

    @Query("""
            SELECT t FROM TradeEvent t
            JOIN FETCH t.security
            JOIN FETCH t.account
            WHERE t.id = :id AND t.portfolio.id = :portfolioId
            """)
    Optional<TradeEvent> findByIdWithDetails(
            @Param("id") UUID id,
            @Param("portfolioId") UUID portfolioId
    );

    /**
     * Used to rebuild holdings from scratch when a corporate action is applied.
     */
    List<TradeEvent> findByPortfolioIdAndSecurityIdOrderByTradeDateAsc(
            UUID portfolioId, UUID securityId
    );
}
