package com.tradetracker.api.portfolio;

import com.tradetracker.calculation.PerformanceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Separate controller for performance endpoints to keep PortfolioController focused.
 * Both map under /v1/portfolios/{portfolioId}.
 */
@RestController
@RequestMapping("/v1/portfolios/{portfolioId}/performance")
@Tag(name = "Portfolios")
@SecurityRequirement(name = "keycloak")
public class PerformanceController {

    private final PerformanceService perfSvc;
    private final com.tradetracker.portfolio.service.PortfolioService portfolioSvc;

    public PerformanceController(
            PerformanceService perfSvc,
            com.tradetracker.portfolio.service.PortfolioService portfolioSvc) {
        this.perfSvc      = perfSvc;
        this.portfolioSvc = portfolioSvc;
    }

    @GetMapping
    @Operation(summary = "Portfolio performance — TWR, MWR, time-series chart data")
    public PerformanceService.PerformanceResult getPerformance(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID portfolioId,
            @RequestParam(defaultValue = "1Y") String period) {

        // Verify ownership
        portfolioSvc.requirePortfolio(jwt.getSubject(), portfolioId);
        return perfSvc.getPerformance(portfolioId, period);
    }
}
