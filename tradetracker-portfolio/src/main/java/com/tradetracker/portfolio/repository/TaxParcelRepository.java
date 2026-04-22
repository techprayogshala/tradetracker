package com.tradetracker.portfolio.repository;

import com.tradetracker.portfolio.entity.TaxParcel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Repository
public interface TaxParcelRepository extends JpaRepository<TaxParcel, UUID> {

    /**
     * All open parcels for a security in a portfolio, ordered for FIFO by default.
     * Parcel matching strategies re-sort in memory from this base result.
     */
    @Query("""
            SELECT p FROM TaxParcel p
            WHERE p.portfolio.id = :portfolioId
              AND p.security.id  = :securityId
              AND p.fullyDisposed = false
            ORDER BY p.acquisitionDate ASC, p.createdAt ASC
            """)
    List<TaxParcel> findOpenParcels(
            @Param("portfolioId") UUID portfolioId,
            @Param("securityId") UUID securityId
    );

    /**
     * All open parcels across a portfolio — used for the tax/open-parcels endpoint.
     */
    @Query("""
            SELECT p FROM TaxParcel p
            JOIN FETCH p.security
            WHERE p.portfolio.id  = :portfolioId
              AND p.fullyDisposed = false
            ORDER BY p.security.ticker ASC, p.acquisitionDate ASC
            """)
    List<TaxParcel> findAllOpenByPortfolio(@Param("portfolioId") UUID portfolioId);

    /**
     * Parcels disposed within a financial year (ATO: 1 July – 30 June).
     */
    @Query("""
            SELECT p FROM TaxParcel p
            JOIN FETCH p.security
            WHERE p.portfolio.id  = :portfolioId
              AND p.fullyDisposed = true
              AND p.disposalDate >= :fyStart
              AND p.disposalDate <= :fyEnd
            ORDER BY p.disposalDate ASC
            """)
    List<TaxParcel> findDisposedInFinancialYear(
            @Param("portfolioId") UUID portfolioId,
            @Param("fyStart") LocalDate fyStart,
            @Param("fyEnd") LocalDate fyEnd
    );

    /**
     * Sum of remaining quantity per security — used to build the holdings view.
     */
    @Query("""
            SELECT p.security.id       AS securityId,
                   SUM(p.quantityRemaining) AS totalQuantity,
                   SUM(p.costPerUnit * p.quantityRemaining) AS totalCostBase
            FROM TaxParcel p
            WHERE p.portfolio.id  = :portfolioId
              AND p.fullyDisposed = false
            GROUP BY p.security.id
            """)
    List<HoldingAggregation> aggregateHoldings(@Param("portfolioId") UUID portfolioId);

    /**
     * Projection used by aggregateHoldings query.
     */
    interface HoldingAggregation {
        UUID getSecurityId();

        BigDecimal getTotalQuantity();

        BigDecimal getTotalCostBase();
    }
}
