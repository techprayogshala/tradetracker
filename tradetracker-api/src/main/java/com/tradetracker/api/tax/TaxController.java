package com.tradetracker.api.tax;

import com.tradetracker.tax.AustralianCgtService;
import com.tradetracker.tax.CgtPdfReportService;
import com.tradetracker.tax.TaxReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.springframework.format.annotation.DateTimeFormat.ISO;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/portfolios/{portfolioId}/tax")
@Tag(name = "Tax & CGT")
@SecurityRequirement(name = "keycloak")
public class TaxController {

    private final TaxReportService taxSvc;
    private final CgtPdfReportService pdfSvc;
    private final org.springframework.jdbc.core.simple.JdbcClient jdbc;

    public TaxController(TaxReportService taxSvc, CgtPdfReportService pdfSvc,
                         org.springframework.jdbc.core.simple.JdbcClient jdbc) {
        this.taxSvc = taxSvc;
        this.pdfSvc = pdfSvc;
        this.jdbc   = jdbc;
    }

    private String fetchPortfolioName(String keycloakSub, UUID portfolioId) {
        return jdbc.sql("""
            SELECT p.name FROM portfolios p
            JOIN users u ON u.id = p.user_id
            WHERE p.id = :pid AND u.keycloak_sub = :sub
            """)
            .param("pid", portfolioId)
            .param("sub", keycloakSub)
            .query(String.class)
            .optional()
            .orElse("My Portfolio");
    }

    @GetMapping("/cgt-summary")
    @Operation(summary = "CGT summary for an Australian financial year (1 Jul – 30 Jun)")
    public AustralianCgtService.TaxYearCgtSummary getCgtSummary(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID portfolioId,
            @RequestParam(defaultValue = "0") int financialYear) {
        int fy = financialYear > 0 ? financialYear : LocalDate.now().getYear();
        return taxSvc.getCgtSummary(jwt.getSubject(), portfolioId, fy);
    }

    @GetMapping("/cgt-events")
    @Operation(summary = "Individual disposal events for the financial year")
    public List<TaxReportService.CgtEventView> getCgtEvents(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID portfolioId,
            @RequestParam(defaultValue = "0") int financialYear,
            @RequestParam(defaultValue = "disposalDate") String sort,
            @RequestParam(defaultValue = "desc") String sortDir) {
        int fy = financialYear > 0 ? financialYear : LocalDate.now().getYear();
        return taxSvc.getCgtEvents(jwt.getSubject(), portfolioId, fy, sort, sortDir);
    }

    @GetMapping("/open-parcels")
    @Operation(summary = "All open tax parcels with unrealised CGT position")
    public List<TaxReportService.OpenParcelView> getOpenParcels(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID portfolioId,
            @RequestParam(defaultValue = "acquisitionDate") String sort,
            @RequestParam(defaultValue = "desc") String sortDir) {
        return taxSvc.getOpenParcels(jwt.getSubject(), portfolioId, sort, sortDir);
    }

    @GetMapping("/dividends")
    @Operation(summary = "Dividend income summary including franking credits")
    public TaxReportService.DividendSummaryView getDividends(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID portfolioId,
            @RequestParam(defaultValue = "0") int financialYear) {
        int fy = financialYear > 0 ? financialYear : LocalDate.now().getYear();
        return taxSvc.getDividendSummary(jwt.getSubject(), portfolioId, fy);
    }

    @GetMapping("/report/pdf")
    @Operation(summary = "Download full ATO CGT report as PDF")
    public ResponseEntity<byte[]> downloadPdf(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID portfolioId,
            @RequestParam(defaultValue = "0") int financialYear) {

        int fy = financialYear > 0 ? financialYear : LocalDate.now().getYear();
        var events  = taxSvc.getCgtEvents(jwt.getSubject(), portfolioId, fy, "disposalDate", "desc");
        var summary = taxSvc.getCgtSummary(jwt.getSubject(), portfolioId, fy);

        // Fetch portfolio name for the report header
        String portfolioName = fetchPortfolioName(jwt.getSubject(), portfolioId);

        byte[] pdf = pdfSvc.generate(portfolioName, fy, events, summary);

        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=\"cgt-report-FY%d.pdf\"".formatted(fy))
            .contentType(MediaType.APPLICATION_PDF)
            .body(pdf);
    }
    @GetMapping("/report/csv")
    @Operation(summary = "Download CGT events as CSV")
    public ResponseEntity<byte[]> downloadCsv(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID portfolioId,
            @RequestParam(defaultValue = "0") int financialYear) {
        int fy = financialYear > 0 ? financialYear : LocalDate.now().getYear();
        List<TaxReportService.CgtEventView> events = taxSvc.getCgtEvents(jwt.getSubject(), portfolioId, fy, "disposalDate", "desc");
        byte[] csv = buildCsv(events);
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=\"cgt-events-FY%d.csv\"".formatted(fy))
            .contentType(MediaType.parseMediaType("text/csv"))
            .body(csv);
    }

    private byte[] buildCsv(List<TaxReportService.CgtEventView> events) {
        var sb = new StringBuilder();
        sb.append("Disposal Date,Ticker,Quantity,Proceeds,Cost Base,Capital Gain,Discount Applied,Assessable Gain,Acquisition Date,Holding Days\n");
        for (var e : events) {
            sb.append(e.disposalDate()).append(',')
              .append(e.ticker()).append(',')
              .append(e.quantity()).append(',')
              .append(e.proceeds()).append(',')
              .append(e.costBase()).append(',')
              .append(e.capitalGain()).append(',')
              .append(e.discountApplied()).append(',')
              .append(e.assessableGain()).append(',')
              .append(e.acquisitionDate()).append(',')
              .append(e.holdingDays()).append('\n');
        }
        return sb.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }
}
