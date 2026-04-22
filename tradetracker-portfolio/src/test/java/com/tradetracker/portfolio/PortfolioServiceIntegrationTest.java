package com.tradetracker.portfolio;

import com.tradetracker.portfolio.service.PortfolioService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
@DisplayName("PortfolioService — Integration Tests")
class PortfolioServiceIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres =
        new PostgreSQLContainer<>("timescale/timescaledb:latest-pg16")
            .withDatabaseName("tradetracker_test")
            .withUsername("tradetracker")
            .withPassword("tradetracker");

    @DynamicPropertySource
    static void overrideProps(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url",         postgres::getJdbcUrl);
        r.add("spring.datasource.username",    postgres::getUsername);
        r.add("spring.datasource.password",    postgres::getPassword);
        r.add("spring.flyway.enabled",         () -> "true");       // re-enable for this test
        r.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }

    @Autowired PortfolioService portfolioService;

    private static final String KC_SUB = "kc|test-" + UUID.randomUUID();

    @BeforeEach
    void provision() {
        portfolioService.provisionUser(KC_SUB, "test@example.com", "Test User");
    }

    @Test @DisplayName("First provision creates user + default portfolio")
    void provision_creates_default_portfolio() {
        List<PortfolioService.PortfolioSummary> ps = portfolioService.listPortfolios(KC_SUB);
        assertThat(ps).hasSize(1);
        assertThat(ps.getFirst().isDefault()).isTrue();
        assertThat(ps.getFirst().name()).isEqualTo("My Portfolio");
    }

    @Test @DisplayName("Provision is idempotent")
    void provision_idempotent() {
        portfolioService.provisionUser(KC_SUB, "test@example.com", "Test User");
        portfolioService.provisionUser(KC_SUB, "test@example.com", "Test User");
        assertThat(portfolioService.listPortfolios(KC_SUB)).hasSize(1);
    }

    @Test @DisplayName("BUY creates parcel with correct cost base (price×qty + fees)")
    void buy_creates_parcel_with_correct_cost_base() {
        UUID pid = firstPortfolioId();
        portfolioService.recordTrade(KC_SUB, pid, new PortfolioService.TradeCommand(
            "CBA", "ASX", "BUY", bd("100"), bd("105.50"), bd("9.95"),
            "AUD", null, LocalDate.of(2024, 3, 15), LocalDate.of(2024, 3, 17),
            null, null, "Initial buy"));

        var h = portfolioService.getHoldings(KC_SUB, pid).getFirst();
        assertThat(h.ticker()).isEqualTo("CBA");
        assertThat(h.quantity()).isEqualByComparingTo("100");
        // 105.50 * 100 + 9.95 = 10559.95
        assertThat(h.costBase()).isEqualByComparingTo("10559.9500");
    }

    @Test @DisplayName("SELL reduces parcel quantity (FIFO)")
    void sell_reduces_parcel_fifo() {
        UUID pid = firstPortfolioId();
        portfolioService.recordTrade(KC_SUB, pid, new PortfolioService.TradeCommand(
            "WBC", "ASX", "BUY", bd("200"), bd("25.00"), bd("9.95"),
            "AUD", null, LocalDate.of(2023, 6, 1), null, null, null, null));
        portfolioService.recordTrade(KC_SUB, pid, new PortfolioService.TradeCommand(
            "WBC", "ASX", "SELL", bd("50"), bd("28.00"), bd("9.95"),
            "AUD", null, LocalDate.of(2024, 1, 15), null, null, null, null));

        var wbc = portfolioService.getHoldings(KC_SUB, pid).stream()
            .filter(h -> "WBC".equals(h.ticker())).findFirst().orElseThrow();
        assertThat(wbc.quantity()).isEqualByComparingTo("150");
    }

    @Test @DisplayName("Selling more than held throws InsufficientHoldingsException")
    void sell_more_than_held_throws() {
        UUID pid = firstPortfolioId();
        portfolioService.recordTrade(KC_SUB, pid, new PortfolioService.TradeCommand(
            "NAB", "ASX", "BUY", bd("10"), bd("30"), BigDecimal.ZERO,
            "AUD", null, LocalDate.of(2024, 1, 1), null, null, null, null));

        assertThatThrownBy(() -> portfolioService.recordTrade(KC_SUB, pid,
            new PortfolioService.TradeCommand(
                "NAB", "ASX", "SELL", bd("20"), bd("32"), BigDecimal.ZERO,
                "AUD", null, LocalDate.of(2024, 6, 1), null, null, null, null)))
            .isInstanceOf(PortfolioService.InsufficientHoldingsException.class);
    }

    @Test @DisplayName("Other user cannot access portfolio")
    void cannot_access_other_users_portfolio() {
        String other = "kc|other-" + UUID.randomUUID();
        portfolioService.provisionUser(other, "other@test.com", "Other");
        UUID otherId = portfolioService.listPortfolios(other).getFirst().id();
        assertThatThrownBy(() -> portfolioService.getHoldings(KC_SUB, otherId))
            .isInstanceOf(PortfolioService.PortfolioNotFoundException.class);
    }

    private UUID firstPortfolioId() {
        return portfolioService.listPortfolios(KC_SUB).getFirst().id();
    }

    private static BigDecimal bd(String v) { return new BigDecimal(v); }
}
