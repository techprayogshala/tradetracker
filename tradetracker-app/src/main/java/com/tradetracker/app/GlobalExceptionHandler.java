package com.tradetracker.app;

import com.tradetracker.portfolio.service.PortfolioService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.net.URI;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Centralised exception → RFC 7807 Problem Detail mapping.
 * Catches exceptions from all controller advice in the app module.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final String BASE = "https://tradetracker.dev/errors/";

    // ── 400 Bad Request ───────────────────────────────────────────────────────

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
            .collect(Collectors.toMap(
                FieldError::getField,
                fe -> fe.getDefaultMessage() == null ? "Invalid" : fe.getDefaultMessage(),
                (a, b) -> a   // keep first error per field
            ));

        ProblemDetail pd = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        pd.setType(URI.create(BASE + "validation-failed"));
        pd.setTitle("Validation Failed");
        pd.setDetail("One or more fields failed validation");
        pd.setProperty("fieldErrors", fieldErrors);
        return pd;
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ProblemDetail handleIllegalArgument(IllegalArgumentException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    // ── 403 Forbidden ─────────────────────────────────────────────────────────

    @ExceptionHandler(AccessDeniedException.class)
    ProblemDetail handleAccessDenied(AccessDeniedException ex) {
        ProblemDetail pd = ProblemDetail.forStatus(HttpStatus.FORBIDDEN);
        pd.setType(URI.create(BASE + "access-denied"));
        pd.setDetail("You do not have permission to access this resource");
        return pd;
    }

    // ── 404 Not Found ─────────────────────────────────────────────────────────

    @ExceptionHandler(PortfolioService.PortfolioNotFoundException.class)
    ProblemDetail handlePortfolioNotFound(PortfolioService.PortfolioNotFoundException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        pd.setType(URI.create(BASE + "portfolio-not-found"));
        return pd;
    }

    @ExceptionHandler(PortfolioService.UserNotFoundException.class)
    ProblemDetail handleUserNotFound(PortfolioService.UserNotFoundException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        pd.setType(URI.create(BASE + "user-not-found"));
        return pd;
    }

    // ── 413 Payload Too Large ─────────────────────────────────────────────────

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ProblemDetail handleFileTooLarge(MaxUploadSizeExceededException ex) {
        ProblemDetail pd = ProblemDetail.forStatus(HttpStatus.PAYLOAD_TOO_LARGE);
        pd.setType(URI.create(BASE + "file-too-large"));
        pd.setDetail("Uploaded file exceeds the maximum allowed size of 10MB");
        return pd;
    }

    // ── 422 Unprocessable Entity ──────────────────────────────────────────────

    @ExceptionHandler(PortfolioService.InsufficientHoldingsException.class)
    ProblemDetail handleInsufficientHoldings(PortfolioService.InsufficientHoldingsException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(
            HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage());
        pd.setType(URI.create(BASE + "insufficient-holdings"));
        pd.setDetail(ex.getMessage());
        return pd;
    }

    // ── 500 Internal Server Error ─────────────────────────────────────────────

    @ExceptionHandler(Exception.class)
    ProblemDetail handleGeneric(Exception ex) {
        log.error("Unhandled exception", ex);
        ProblemDetail pd = ProblemDetail.forStatus(HttpStatus.INTERNAL_SERVER_ERROR);
        pd.setType(URI.create(BASE + "internal-error"));
        pd.setDetail("An unexpected error occurred. Please try again.");
        return pd;
    }
}
