package com.tradetracker.portfolio.repository;

import com.tradetracker.portfolio.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

// =============================================================================
// UserRepository
// =============================================================================

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByKeycloakSub(String keycloakSub);
    boolean existsByEmail(String email);
}

// =============================================================================
// SecurityRepository
// =============================================================================

// =============================================================================
// PortfolioRepository
// =============================================================================

// =============================================================================
// AccountRepository
// =============================================================================

// =============================================================================
// TradeEventRepository
// =============================================================================

// =============================================================================
// TaxParcelRepository
// =============================================================================

