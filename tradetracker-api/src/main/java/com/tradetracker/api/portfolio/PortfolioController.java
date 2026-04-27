package com.tradetracker.api.portfolio;

import com.tradetracker.portfolio.entity.TradeEvent;
import com.tradetracker.portfolio.service.PortfolioService;
import com.tradetracker.portfolio.service.PortfolioService.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/portfolios")
@Tag(name = "Portfolios")
@SecurityRequirement(name = "keycloak")
public class PortfolioController {

    private final PortfolioService svc;

    public PortfolioController(PortfolioService svc) {
        this.svc = svc;
    }

    // ── Provision (first login) ───────────────────────────────────────────────

    @PostMapping("/provision")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Provision user record on first login — idempotent, call on every auth")
    public void provision(@AuthenticationPrincipal Jwt jwt) {
        svc.provisionUser(
            jwt.getSubject(),
            jwt.getClaimAsString("email"),
            jwt.getClaimAsString("name")
        );
    }

    // ── Portfolios ────────────────────────────────────────────────────────────

    @GetMapping
    @Operation(summary = "List all portfolios for the authenticated user")
    public List<PortfolioSummary> list(@AuthenticationPrincipal Jwt jwt) {
        return svc.listPortfolios(jwt.getSubject());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a new portfolio")
    public PortfolioSummary create(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CreatePortfolioRequest req) {
        var p = svc.createPortfolio(
            jwt.getSubject(), req.name(), 
            req.baseCurrency() != null ? req.baseCurrency() : "AUD",
            req.parcelMatchingStrategy(), Boolean.TRUE.equals(req.makeDefault()));
        // Re-query to get the enriched summary (total value, gains etc.)
        return svc.listPortfolios(jwt.getSubject()).stream()
            .filter(s -> s.id().equals(p.getId()))
            .findFirst().orElseThrow();
    }

    @GetMapping("/{portfolioId}")
    @Operation(summary = "Get a single portfolio summary by ID")
    public PortfolioSummary getOne(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID portfolioId) {
        return svc.listPortfolios(jwt.getSubject()).stream()
            .filter(p -> p.id().equals(portfolioId))
            .findFirst()
            .orElseThrow(() -> new PortfolioService.PortfolioNotFoundException(portfolioId));
    }

    @PutMapping("/{portfolioId}")
    @Operation(summary = "Update portfolio name, strategy or default flag")
    public PortfolioSummary update(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID portfolioId,
            @Valid @RequestBody UpdatePortfolioRequest req) {
        svc.updatePortfolio(jwt.getSubject(), portfolioId,
            req.name(), req.description(), req.parcelMatchingStrategy(), req.makeDefault());
        return svc.listPortfolios(jwt.getSubject()).stream()
            .filter(s -> s.id().equals(portfolioId))
            .findFirst().orElseThrow();
    }

    @DeleteMapping("/{portfolioId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete a portfolio and all its trade history")
    public void delete(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID portfolioId) {
        svc.deletePortfolio(jwt.getSubject(), portfolioId);
    }

    // ── Holdings ──────────────────────────────────────────────────────────────

    @GetMapping("/{portfolioId}/holdings")
    @Operation(summary = "Current holdings enriched with market price and unrealised P&L")
    public List<HoldingView> holdings(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID portfolioId,
            @RequestParam(defaultValue = "ticker") String sort,
            @RequestParam(defaultValue = "asc") String sortDir,
            @RequestParam(defaultValue = "false") boolean includeDisposed) {
        return svc.getHoldings(jwt.getSubject(), portfolioId, sort, sortDir, includeDisposed);
    }

    // ── Trades ────────────────────────────────────────────────────────────────

    @GetMapping("/{portfolioId}/trades")
    @Operation(summary = "Paginated trade history with optional filters")
    public Page<TradeDto> listTrades(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID portfolioId,
            @RequestParam(required = false) String ticker,
            @RequestParam(required = false) String tradeType,
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to,
            @RequestParam(defaultValue = "tradeDate") String sort,
            @RequestParam(defaultValue = "desc") String sortDir,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "25") int size) {

        var direction = "asc".equalsIgnoreCase(sortDir) ? Sort.Direction.ASC : Sort.Direction.DESC;
        var pageable = PageRequest.of(page, size, Sort.by(direction, sort));
        return svc.listTrades(jwt.getSubject(), portfolioId, ticker, tradeType, from, to, pageable)
            .map(t -> new TradeDto(
                t.getId(),
                t.getSecurity().getTicker(),
                t.getSecurity().getExchange(),
                t.getTradeType().name(),
                t.getQuantity(), t.getPrice(), t.getFees(), t.totalCost(),
                t.getCurrency(), t.getFxRateToBase(),
                t.getTradeDate(), t.getSettlementDate(),
                t.getSource().name(), t.getExternalRef(), t.getNotes()
            ));
    }

    @PostMapping("/{portfolioId}/trades")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Record a new trade — BUY creates a parcel, SELL matches open parcels")
    public TradeDto createTrade(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID portfolioId,
            @Valid @RequestBody CreateTradeRequest req) {
        var t = svc.recordTrade(jwt.getSubject(), portfolioId, new TradeCommand(
            req.ticker(), req.exchange(), req.tradeType(),
            req.quantity(), req.price(), req.fees(), req.currency(), req.fxRateToBase(),
            req.tradeDate(), req.settlementDate(), req.accountId(), req.externalRef(), req.notes(),
            TradeEvent.Source.MANUAL
        ));
        return new TradeDto(
            t.getId(),
            t.getSecurity().getTicker(), t.getSecurity().getExchange(),
            t.getTradeType().name(),
            t.getQuantity(), t.getPrice(), t.getFees(), t.totalCost(),
            t.getCurrency(), t.getFxRateToBase(),
            t.getTradeDate(), t.getSettlementDate(),
            t.getSource().name(), t.getExternalRef(), t.getNotes()
        );
    }

    @DeleteMapping("/{portfolioId}/trades/{tradeId}")
    @Operation(summary = "Delete a trade by ID")
    public void deleteTrade(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID portfolioId,
            @PathVariable UUID tradeId) {
        svc.deleteTrade(jwt.getSubject(), portfolioId, tradeId);
    }

    @DeleteMapping("/{portfolioId}/trades")
    @Operation(summary = "Delete multiple trades by IDs")
    public void deleteTrades(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID portfolioId,
            @RequestParam String ids) {
        String[] idArray = ids.split(",");
        for (String id : idArray) {
            svc.deleteTrade(jwt.getSubject(), portfolioId, UUID.fromString(id.trim()));
        }
    }

    @PutMapping("/{portfolioId}/trades/{tradeId}")
    @Operation(summary = "Update an existing trade")
    public TradeDto updateTrade(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID portfolioId,
            @PathVariable UUID tradeId,
            @Valid @RequestBody CreateTradeRequest req) {
        var t = svc.updateTrade(jwt.getSubject(), portfolioId, tradeId, new TradeCommand(
            req.ticker(), req.exchange(), req.tradeType(),
            req.quantity(), req.price(), req.fees(), req.currency(), req.fxRateToBase(),
            req.tradeDate(), req.settlementDate(), req.accountId(), req.externalRef(), req.notes(),
            null
        ));
        return new TradeDto(
            t.getId(),
            t.getSecurity().getTicker(), t.getSecurity().getExchange(),
            t.getTradeType().name(),
            t.getQuantity(), t.getPrice(), t.getFees(), t.totalCost(),
            t.getCurrency(), t.getFxRateToBase(),
            t.getTradeDate(), t.getSettlementDate(),
            t.getSource().name(), t.getExternalRef(), t.getNotes()
        );
    }

    // ── Request / Response DTOs ───────────────────────────────────────────────

    public record CreatePortfolioRequest(
        @NotBlank String name,
        @Pattern(regexp = "[A-Z]{3}") String baseCurrency,
        String parcelMatchingStrategy,
        Boolean makeDefault
    ) {}

    public record UpdatePortfolioRequest(
        String name, String description,
        String parcelMatchingStrategy, Boolean makeDefault
    ) {}

    public record CreateTradeRequest(
        @NotBlank  String ticker,
        @NotBlank  String exchange,
        @NotBlank  String tradeType,
        @NotNull @Positive       BigDecimal quantity,
        @NotNull @PositiveOrZero BigDecimal price,
        @NotNull @PositiveOrZero BigDecimal fees,
        @NotBlank @Pattern(regexp = "[A-Z]{3}") String currency,
        BigDecimal fxRateToBase,
        @NotNull LocalDate tradeDate,
        LocalDate settlementDate,
        UUID accountId,
        String externalRef,
        String notes
    ) {}

    @PostMapping("/{portfolioId}/trades/bulk")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    @Operation(summary = "Bulk import trades from array — each trade creates a parcel")
    public List<TradeDto> bulkCreateTrades(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID portfolioId,
            @Valid @RequestBody List<CreateTradeRequest> trades) {

        // Sort trades: process by date (oldest first, nulls last), then type (BUYs before SELLs)
        var sortedTrades = trades.stream()
            .sorted(Comparator.comparing(CreateTradeRequest::tradeDate, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(req -> "BUY".equalsIgnoreCase(req.tradeType()) ? 0 : 1))
            .toList();

        List<TradeDto> results = new ArrayList<>();
        for (var req : sortedTrades) {
            var t = svc.recordTrade(jwt.getSubject(), portfolioId, new TradeCommand(
                req.ticker(), req.exchange(), req.tradeType(),
                req.quantity(), req.price(), req.fees(), req.currency(), req.fxRateToBase(),
                req.tradeDate(), req.settlementDate(), req.accountId(), req.externalRef(), req.notes(),
                TradeEvent.Source.CSV_IMPORT
            ));
            results.add(new TradeDto(
                t.getId(),
                t.getSecurity().getTicker(), t.getSecurity().getExchange(),
                t.getTradeType().name(),
                t.getQuantity(), t.getPrice(), t.getFees(), t.totalCost(),
                t.getCurrency(), t.getFxRateToBase(),
                t.getTradeDate(), t.getSettlementDate(),
                t.getSource().name(), t.getExternalRef(), t.getNotes()
            ));
        }
        return results;
    }

    public record TradeDto(
        UUID id, String ticker, String exchange, String tradeType,
        BigDecimal quantity, BigDecimal price, BigDecimal fees, BigDecimal totalCost,
        String currency, BigDecimal fxRateToBase,
        LocalDate tradeDate, LocalDate settlementDate,
        String source, String externalRef, String notes
    ) {}
}
