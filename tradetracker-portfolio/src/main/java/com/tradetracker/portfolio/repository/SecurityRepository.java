package com.tradetracker.portfolio.repository;

import com.tradetracker.portfolio.entity.Security;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SecurityRepository extends JpaRepository<Security, UUID> {
    Optional<Security> findByTickerAndExchange(String ticker, String exchange);

    @Query("""
            SELECT s FROM Security s
            WHERE UPPER(s.ticker) LIKE UPPER(CONCAT('%', :q, '%'))
               OR UPPER(s.name)   LIKE UPPER(CONCAT('%', :q, '%'))
            ORDER BY s.ticker
            """)
    List<Security> search(@Param("q") String query, Pageable pageable);
}
