package com.tradetracker.portfolio.service;

import com.tradetracker.portfolio.entity.*;
import com.tradetracker.portfolio.event.PortfolioCreatedEvent;
import com.tradetracker.portfolio.event.TradeRecordedEvent;
import com.tradetracker.portfolio.repository.*;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Core portfolio service.
 *
 * Responsibilities:
 *  - User provisioning (first login)
 *  - Portfolio and account CRUD
 *  - Trade recording (BUY creates parcels, SELL matches and reduces parcels)
 *  - Holdings aggregation from open parcels + current prices
 */
@Service
@Transactional
public class PortfolioService {

    private final UserRepository       userRepo;
    private final PortfolioRepository  portfolioRepo;
    private final AccountRepository    accountRepo;
    private final SecurityRepository   securityRepo;
    private final TradeEventRepository tradeRepo;
    private final TaxParcelRepository  parcelRepo;
    private final PriceService         priceService;
    private final ApplicationEventPublisher events;
    private final org.springframework.jdbc.core.simple.JdbcClient jdbc;
    private final java.util.Map<String, com.tradetracker.portfolio.matching.ParcelMatchingStrategy> matchingStrategies;

    // Self-reference injected lazily so Spring AOP proxy intercepts @Cacheable on getHoldings
    // when called from toSummary (direct this.getHoldings() bypasses the proxy).
    @org.springframework.context.annotation.Lazy
    @org.springframework.beans.factory.annotation.Autowired
    private PortfolioService self;

    public PortfolioService(
            UserRepository userRepo,
            PortfolioRepository portfolioRepo,
            AccountRepository accountRepo,
            SecurityRepository securityRepo,
            TradeEventRepository tradeRepo,
            TaxParcelRepository parcelRepo,
            PriceService priceService,
            ApplicationEventPublisher events,
            org.springframework.jdbc.core.simple.JdbcClient jdbc,
            java.util.List<com.tradetracker.portfolio.matching.ParcelMatchingStrategy> strategyList) {
        this.userRepo      = userRepo;
        this.portfolioRepo = portfolioRepo;
        this.accountRepo   = accountRepo;
        this.securityRepo  = securityRepo;
        this.tradeRepo     = tradeRepo;
        this.parcelRepo    = parcelRepo;
        this.priceService  = priceService;
        this.events        = events;
        this.jdbc          = jdbc;
        // Index strategies by name (FIFO, LIFO, MINIMISE_CGT) for O(1) lookup
        this.matchingStrategies = strategyList.stream()
            .collect(java.util.stream.Collectors.toMap(
                com.tradetracker.portfolio.matching.ParcelMatchingStrategy::strategyName,
                s -> s));
    }

    // ── User provisioning ────────────────────────────────────────────────────

    /**
     * Called on first login. Creates the user record and a default portfolio.
     * Idempotent — safe to call on every login.
     */
    public User provisionUser(String keycloakSub, String email, String displayName) {
        return userRepo.findByKeycloakSub(keycloakSub).orElseGet(() -> {
            User user = new User(keycloakSub, email, displayName);
            userRepo.save(user);

            Portfolio defaultPortfolio = Portfolio.createDefault(user);
            portfolioRepo.save(defaultPortfolio);

            Account defaultAccount = new Account(defaultPortfolio, "Default Account", user.getBaseCurrency());
            accountRepo.save(defaultAccount);

            return user;
        });
    }

    // ── Portfolios ───────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    // @Cacheable disabled - Redis serialization issues with record types
    public List<PortfolioSummary> listPortfolios(String keycloakSub) {
        return portfolioRepo
            .findByUserKeycloakSubOrderByIsDefaultDescNameAsc(keycloakSub)
            .stream()
            .map(this::toSummary)
            .toList();
    }

    public Portfolio createPortfolio(String keycloakSub, String name, String currency,
                                     String strategy, boolean makeDefault) {
        User user = requireUser(keycloakSub);

        if (makeDefault) {
            portfolioRepo.clearDefaultForUser(keycloakSub);
        }

        Portfolio p = new Portfolio(user, name, currency);
        if (strategy != null) {
            p.setParcelMatchingStrategy(Portfolio.ParcelMatchingStrategy.valueOf(strategy));
        }
        p.setDefault(makeDefault);
        portfolioRepo.save(p);

        // Every portfolio gets an initial default account
        accountRepo.save(new Account(p, "Default Account", currency));

        events.publishEvent(new PortfolioCreatedEvent(p.getId(), keycloakSub));
        return p;
    }

    @Transactional(readOnly = true)
    public Portfolio requirePortfolio(String keycloakSub, UUID portfolioId) {
        return portfolioRepo.findByIdAndUserKeycloakSub(portfolioId, keycloakSub)
            .orElseThrow(() -> new PortfolioNotFoundException(portfolioId));
    }

    @CacheEvict(value = "portfolios", key = "#keycloakSub")
    public Portfolio updatePortfolio(String keycloakSub, UUID portfolioId,
                                     String name, String description,
                                     String strategy, Boolean makeDefault) {
        Portfolio p = requirePortfolio(keycloakSub, portfolioId);

        if (name != null)        p.setName(name);
        if (description != null) p.setDescription(description);
        if (strategy != null)    p.setParcelMatchingStrategy(
            Portfolio.ParcelMatchingStrategy.valueOf(strategy));

        if (Boolean.TRUE.equals(makeDefault)) {
            portfolioRepo.clearDefaultForUser(keycloakSub);
            p.setDefault(true);
        }

        return portfolioRepo.save(p);
    }

    @CacheEvict(value = "portfolios", key = "#keycloakSub")
    public void deletePortfolio(String keycloakSub, UUID portfolioId) {
        Portfolio p = requirePortfolio(keycloakSub, portfolioId);
        portfolioRepo.delete(p);
    }

    // ── Holdings ─────────────────────────────────────────────────────────────

    /**
     * Aggregates open parcels by security and enriches with current market price.
     * Result is Redis-cached for 5 minutes (TTL in application.yml).
     */
    @Transactional(readOnly = true)
    // @Cacheable(value = "holdings", key = "#portfolioId") // Disabled - causes Jackson deserialization issues
    public List<HoldingView> getHoldings(String keycloakSub, UUID portfolioId) {
        requirePortfolio(keycloakSub, portfolioId);

        List<TaxParcelRepository.HoldingAggregation> aggregations =
            parcelRepo.aggregateHoldings(portfolioId);

        if (aggregations.isEmpty()) return List.of();

        // Batch-load securities and their current prices
        List<UUID> securityIds = aggregations.stream()
            .map(TaxParcelRepository.HoldingAggregation::getSecurityId)
            .toList();

        Map<UUID, Security> securities = securityRepo.findAllById(securityIds).stream()
            .collect(Collectors.toMap(Security::getId, Function.identity()));

        Map<UUID, BigDecimal> prices = priceService.getCurrentPrices(securityIds);

        // Earliest parcel per security (for CGT discount eligibility display)
        Map<UUID, LocalDate> oldestDates = parcelRepo
            .findAllOpenByPortfolio(portfolioId).stream()
            .collect(Collectors.toMap(
                p -> p.getSecurity().getId(),
                TaxParcel::getAcquisitionDate,
                (a, b) -> a.isBefore(b) ? a : b   // keep earliest
            ));

        return aggregations.stream().map(agg -> {
            Security security    = securities.get(agg.getSecurityId());
            BigDecimal price     = prices.getOrDefault(agg.getSecurityId(), BigDecimal.ZERO);
            BigDecimal marketValue = price.multiply(agg.getTotalQuantity())
                .setScale(4, RoundingMode.HALF_UP);
            BigDecimal costBase  = agg.getTotalCostBase().setScale(4, RoundingMode.HALF_UP);
            BigDecimal gain      = marketValue.subtract(costBase);
            BigDecimal gainPct   = costBase.compareTo(BigDecimal.ZERO) == 0
                ? BigDecimal.ZERO
                : gain.divide(costBase, 6, RoundingMode.HALF_UP);
            BigDecimal avgCost   = agg.getTotalQuantity().compareTo(BigDecimal.ZERO) == 0
                ? BigDecimal.ZERO
                : costBase.divide(agg.getTotalQuantity(), 6, RoundingMode.HALF_UP);

            return new HoldingView(
                security.getId(),
                security.getTicker(),
                security.getExchange(),
                security.getName(),
                agg.getTotalQuantity(),
                price,
                marketValue,
                costBase,
                gain,
                gainPct,
                avgCost,
                oldestDates.get(agg.getSecurityId())
            );
        }).toList();
    }

    // ── Trades ───────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<TradeEvent> listTrades(String keycloakSub, UUID portfolioId,
                                       String ticker, String tradeType,
                                       LocalDate from, LocalDate to,
                                       Pageable pageable) {
        requirePortfolio(keycloakSub, portfolioId);
        TradeEvent.TradeType typeEnum = tradeType != null ? TradeEvent.TradeType.valueOf(tradeType) : null;
        return tradeRepo.findFiltered(portfolioId, ticker, typeEnum, from, to, pageable);
    }

    /**
     * Records a new trade and performs the appropriate parcel operation:
     *  - BUY         → creates a new TaxParcel
     *  - SELL        → matches against open parcels using the portfolio's strategy
     *  - DIVIDEND    → records income (no parcel effect)
     *  - RETURN_OF_CAPITAL → reduces cost base of all open parcels for the security
     */
    @Caching(evict = {
        @CacheEvict(value = "holdings",   key = "#portfolioId"),
        @CacheEvict(value = "portfolios", allEntries = true),
        @CacheEvict(value = "performance",allEntries = true)
    })
    public TradeEvent recordTrade(String keycloakSub, UUID portfolioId,
                                  TradeCommand cmd) {
        Portfolio portfolio = requirePortfolio(keycloakSub, portfolioId);

        Security security = securityRepo
            .findByTickerAndExchange(cmd.ticker().toUpperCase(), cmd.exchange().toUpperCase())
            .orElseGet(() -> securityRepo.save(
                new Security(cmd.ticker(), cmd.exchange(), cmd.currency())));

        // Use first account if not specified
        Account account = cmd.accountId() != null
            ? accountRepo.findByIdAndPortfolioId(cmd.accountId(), portfolioId)
                .orElseThrow(() -> new IllegalArgumentException("Account not found"))
            : accountRepo.findByPortfolioId(portfolioId).getFirst();

        TradeEvent trade = TradeEvent.builder()
            .portfolio(portfolio)
            .account(account)
            .security(security)
            .tradeType(TradeEvent.TradeType.valueOf(cmd.tradeType()))
            .quantity(cmd.quantity())
            .price(cmd.price())
            .fees(cmd.fees())
            .currency(cmd.currency())
            .fxRateToBase(cmd.fxRateToBase())
            .tradeDate(cmd.tradeDate())
            .settlementDate(cmd.settlementDate())
            .notes(cmd.notes())
            .externalRef(cmd.externalRef())
            .build();

        tradeRepo.save(trade);

        switch (trade.getTradeType()) {
            case BUY -> createParcel(portfolio, security, trade);
            case SELL -> matchAndReduceParcels(portfolio, security, trade);
            case RETURN_OF_CAPITAL -> reduceCostBase(portfolio, security, trade);
            default -> { /* DIVIDEND, TRANSFER_IN, TRANSFER_OUT: no parcel effect */ }
        }

        events.publishEvent(new TradeRecordedEvent(trade.getId(), portfolioId, trade.getTradeType()));
        return trade;
    }

    // ── Parcel operations ────────────────────────────────────────────────────

    private void createParcel(Portfolio portfolio, Security security, TradeEvent trade) {
        TaxParcel parcel = new TaxParcel(
            portfolio,
            security,
            trade,
            trade.getQuantity(),
            trade.costPerUnit(),
            trade.getCurrency(),
            trade.getTradeDate()
        );
        parcel.setFxRateToBase(trade.getFxRateToBase());
        parcelRepo.save(parcel);
    }

    private void matchAndReduceParcels(Portfolio portfolio, Security security, TradeEvent sell) {
        List<TaxParcel> openParcels = parcelRepo.findOpenParcels(portfolio.getId(), security.getId());

        // Look up the portfolio's configured strategy (FIFO / LIFO / MINIMISE_CGT)
        com.tradetracker.portfolio.matching.ParcelMatchingStrategy strategy =
            matchingStrategies.getOrDefault(
                portfolio.getParcelMatchingStrategy().name(),
                matchingStrategies.get("FIFO"));   // safe default

        // Build the sell event and open-parcel views for the strategy
        var sellEvent = new com.tradetracker.portfolio.matching.ParcelMatchingStrategy.SellEvent(
            sell.getId(), portfolio.getId(), security.getId(),
            sell.getQuantity(), sell.getPrice(), sell.getTradeDate());

        List<com.tradetracker.portfolio.matching.ParcelMatchingStrategy.OpenParcel> views = openParcels.stream()
            .map(p -> new com.tradetracker.portfolio.matching.ParcelMatchingStrategy.OpenParcel(
                p.getId(), p.getQuantityRemaining(),
                p.getCostPerUnit(), p.getAcquisitionDate()))
            .toList();

        List<com.tradetracker.portfolio.matching.ParcelMatchingStrategy.ParcelAllocation> allocations;
        try {
            allocations = strategy.match(sellEvent, views);
        } catch (com.tradetracker.portfolio.matching.InsufficientHoldingsException e) {
            throw new InsufficientHoldingsException(e.getMessage());
        }

        // Apply allocations back to entity state and write audit ledger
        java.util.Map<java.util.UUID, TaxParcel> byId = openParcels.stream()
            .collect(java.util.stream.Collectors.toMap(TaxParcel::getId, p -> p));

        for (var alloc : allocations) {
            TaxParcel parcel = byId.get(alloc.parcelId());
            if (parcel == null) continue;

            parcel.recordDisposal(alloc.quantityToDispose(), sell.getTradeDate());
            parcelRepo.save(parcel);
            writeDisposalLedger(sell, parcel, alloc);
        }
    }

    private void writeDisposalLedger(TradeEvent sell, TaxParcel parcel,
            com.tradetracker.portfolio.matching.ParcelMatchingStrategy.ParcelAllocation alloc) {
        // Capital gain = (disposal price - cost per unit) * quantity
        java.math.BigDecimal gain = alloc.proceedsPerUnit()
            .subtract(parcel.getCostPerUnit())
            .multiply(alloc.quantityToDispose())
            .setScale(8, java.math.RoundingMode.HALF_UP);

        boolean discountEligible =
            parcel.getAcquisitionDate().plusYears(1).isBefore(sell.getTradeDate());

        jdbc.sql("""
            INSERT INTO parcel_disposals
                (sell_trade_id, parcel_id, quantity_disposed, disposal_price,
                 disposal_date, capital_gain, cgt_discount_applied)
            VALUES
                (:sellTradeId, :parcelId, :qty, :price, :date, :gain, :discount)
            """)
            .param("sellTradeId", sell.getId())
            .param("parcelId",    parcel.getId())
            .param("qty",         alloc.quantityToDispose())
            .param("price",       alloc.proceedsPerUnit())
            .param("date",        sell.getTradeDate())
            .param("gain",        gain)
            .param("discount",    discountEligible)
            .update();
    }

    private void reduceCostBase(Portfolio portfolio, Security security, TradeEvent roc) {
        List<TaxParcel> openParcels = parcelRepo.findOpenParcels(portfolio.getId(), security.getId());
        if (openParcels.isEmpty()) return;

        // For Return of Capital: the trade's 'price' field holds the distribution
        // per share (e.g. $0.10 per share).  This is the ATO-correct treatment —
        // RoC is not assessable income; it reduces the cost base instead.
        BigDecimal reductionPerUnit = roc.getPrice();
        if (reductionPerUnit.compareTo(BigDecimal.ZERO) <= 0) return;

        for (TaxParcel parcel : openParcels) {
            BigDecimal oldCost = parcel.getCostPerUnit();
            // Cost base cannot go below zero — excess becomes a capital gain
            BigDecimal newCost = oldCost.subtract(reductionPerUnit).max(BigDecimal.ZERO);

            parcel.setCostPerUnit(newCost);
            parcelRepo.save(parcel);

            // Write immutable audit row
            jdbc.sql("""
                INSERT INTO cost_base_adjustments
                    (portfolio_id, security_id, trade_event_id, parcel_id,
                     adjustment_type, old_cost_per_unit, new_cost_per_unit,
                     reduction_per_unit, quantity_held, effective_date, notes)
                VALUES
                    (:pid, :sid, :tid, :parcelId,
                     'RETURN_OF_CAPITAL', :old, :new,
                     :reduction, :qty, :date, :notes)
                """)
                .param("pid",       portfolio.getId())
                .param("sid",       security.getId())
                .param("tid",       roc.getId())
                .param("parcelId",  parcel.getId())
                .param("old",       oldCost)
                .param("new",       newCost)
                .param("reduction", reductionPerUnit)
                .param("qty",       parcel.getQuantityRemaining())
                .param("date",      roc.getTradeDate())
                .param("notes",     roc.getNotes())
                .update();
        }
    }

    // ── Portfolio summary ────────────────────────────────────────────────────

    private PortfolioSummary toSummary(Portfolio p) {
        // Use self (the Spring proxy) so @Cacheable on getHoldings is properly intercepted.
        List<HoldingView> holdings = self.getHoldings(p.getUser().getKeycloakSub(), p.getId());
        BigDecimal totalValue    = holdings.stream().map(HoldingView::marketValue)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalCostBase = holdings.stream().map(HoldingView::costBase)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal unrealisedGL  = totalValue.subtract(totalCostBase);
        BigDecimal unrealisedPct = totalCostBase.compareTo(BigDecimal.ZERO) == 0
            ? BigDecimal.ZERO
            : unrealisedGL.divide(totalCostBase, 6, RoundingMode.HALF_UP);

        return new PortfolioSummary(p.getId(), p.getName(), p.getBaseCurrency(),
            p.getParcelMatchingStrategy().name(), p.isDefault(),
            totalValue, totalCostBase, unrealisedGL, unrealisedPct);
    }

    private User requireUser(String keycloakSub) {
        return userRepo.findByKeycloakSub(keycloakSub)
            .orElseThrow(() -> new UserNotFoundException(keycloakSub));
    }

    // ── Value objects ────────────────────────────────────────────────────────

    public record TradeCommand(
        String ticker, String exchange, String tradeType,
        BigDecimal quantity, BigDecimal price, BigDecimal fees,
        String currency, BigDecimal fxRateToBase,
        LocalDate tradeDate, LocalDate settlementDate,
        UUID accountId, String externalRef, String notes
    ) {}

    public record PortfolioSummary(
        UUID id, String name, String baseCurrency,
        String parcelMatchingStrategy, boolean isDefault,
        BigDecimal totalValue, BigDecimal totalCostBase,
        BigDecimal unrealisedGainLoss, BigDecimal unrealisedGainLossPct
    ) {}

    public record HoldingView(
        UUID securityId, String ticker, String exchange, String securityName,
        BigDecimal quantity, BigDecimal currentPrice, BigDecimal marketValue,
        BigDecimal costBase, BigDecimal unrealisedGain, BigDecimal unrealisedGainPct,
        BigDecimal averageCostPerUnit, LocalDate oldestParcelDate
    ) {}

    // ── Exceptions ───────────────────────────────────────────────────────────

    public static class PortfolioNotFoundException extends RuntimeException {
        public PortfolioNotFoundException(UUID id) {
            super("Portfolio not found: " + id);
        }
    }

    public static class UserNotFoundException extends RuntimeException {
        public UserNotFoundException(String sub) {
            super("User not found for keycloak sub: " + sub);
        }
    }

    public static class InsufficientHoldingsException extends RuntimeException {
        public InsufficientHoldingsException(String msg) { super(msg); }
    }
}
