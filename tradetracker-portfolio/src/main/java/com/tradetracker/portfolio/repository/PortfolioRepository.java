package com.tradetracker.portfolio.repository;

import com.tradetracker.portfolio.entity.Portfolio;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PortfolioRepository extends JpaRepository<Portfolio, UUID> {

    List<Portfolio> findByUserKeycloakSubOrderByIsDefaultDescNameAsc(String keycloakSub);

    Optional<Portfolio> findByIdAndUserKeycloakSub(UUID id, String keycloakSub);

    boolean existsByIdAndUserKeycloakSub(UUID id, String keycloakSub);

    /**
     * Clears the default flag on all portfolios for a user before setting a new default.
     */
    @Modifying
    @Query("UPDATE Portfolio p SET p.isDefault = false WHERE p.user.keycloakSub = :sub")
    void clearDefaultForUser(@Param("sub") String keycloakSub);
}
