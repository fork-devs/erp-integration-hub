package com.erp.integrationhub.interfaces.rest.advice;

import com.erp.integrationhub.domain.exception.DomainException;
import com.erp.integrationhub.domain.exception.IdempotencyConflictException;
import com.erp.integrationhub.domain.exception.InvalidSignatureException;
import com.erp.integrationhub.domain.exception.IntegrationValidationException;
import com.erp.integrationhub.domain.exception.MaxRetriesExceededException;
import com.erp.integrationhub.domain.exception.ProviderUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalRestExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalRestExceptionHandler.class);

    @ExceptionHandler(IdempotencyConflictException.class)
    public ProblemDetail handleIdempotencyConflict(IdempotencyConflictException ex) {
        log.warn("Idempotency conflict: {}", ex.getMessage());
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        problem.setTitle("Idempotency Conflict");
        problem.setType(URI.create("https://erp-automotive.pe/errors/idempotency-conflict"));
        problem.setProperty("timestamp", Instant.now());
        return problem;
    }

    @ExceptionHandler(InvalidSignatureException.class)
    public ProblemDetail handleInvalidSignature(InvalidSignatureException ex) {
        log.warn("Invalid webhook signature: {}", ex.getMessage());
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, ex.getMessage());
        problem.setTitle("Invalid Webhook Signature");
        problem.setType(URI.create("https://erp-automotive.pe/errors/invalid-signature"));
        problem.setProperty("timestamp", Instant.now());
        return problem;
    }

    @ExceptionHandler(com.erp.integrationhub.domain.exception.TenantAccessDeniedException.class)
    public ProblemDetail handleTenantAccessDenied(com.erp.integrationhub.domain.exception.TenantAccessDeniedException ex) {
        log.warn("Tenant access denied / isolation violation: {}", ex.getMessage());
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, ex.getMessage());
        problem.setTitle("Tenant Access Forbidden");
        problem.setType(URI.create("https://erp-automotive.pe/errors/tenant-access-forbidden"));
        problem.setProperty("timestamp", Instant.now());
        return problem;
    }

    @ExceptionHandler(ProviderUnavailableException.class)
    public ProblemDetail handleProviderUnavailable(ProviderUnavailableException ex) {
        log.error("Provider unavailable: {}", ex.getMessage());
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, ex.getMessage());
        problem.setTitle("External Provider Unavailable");
        problem.setType(URI.create("https://erp-automotive.pe/errors/provider-unavailable"));
        problem.setProperty("timestamp", Instant.now());
        return problem;
    }

    @ExceptionHandler(IntegrationValidationException.class)
    public ProblemDetail handleIntegrationValidation(IntegrationValidationException ex) {
        log.warn("Integration validation failed: {}", ex.getMessage());
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        problem.setTitle("Validation Failed");
        problem.setType(URI.create("https://erp-automotive.pe/errors/validation-failed"));
        problem.setProperty("timestamp", Instant.now());
        return problem;
    }

    @ExceptionHandler(MaxRetriesExceededException.class)
    public ProblemDetail handleMaxRetriesExceeded(MaxRetriesExceededException ex) {
        log.error("Max retries exceeded: {}", ex.getMessage());
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_GATEWAY, ex.getMessage());
        problem.setTitle("Max Retries Exceeded (DLQ)");
        problem.setType(URI.create("https://erp-automotive.pe/errors/max-retries-exceeded"));
        problem.setProperty("timestamp", Instant.now());
        return problem;
    }

    @ExceptionHandler(DomainException.class)
    public ProblemDetail handleDomainException(DomainException ex) {
        log.warn("Domain exception: {}", ex.getMessage());
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        problem.setTitle("Domain Error");
        problem.setType(URI.create("https://erp-automotive.pe/errors/domain-error"));
        problem.setProperty("timestamp", Instant.now());
        return problem;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidationExceptions(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            errors.put(error.getField(), error.getDefaultMessage());
        }
        log.warn("Method argument validation errors: {}", errors);
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Validation failed for one or more fields");
        problem.setTitle("Constraint Violation");
        problem.setType(URI.create("https://erp-automotive.pe/errors/constraint-violation"));
        problem.setProperty("errors", errors);
        problem.setProperty("timestamp", Instant.now());
        return problem;
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail handleIllegalArgument(IllegalArgumentException ex) {
        log.warn("Illegal argument: {}", ex.getMessage());
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        problem.setTitle("Illegal Argument");
        problem.setType(URI.create("https://erp-automotive.pe/errors/illegal-argument"));
        problem.setProperty("timestamp", Instant.now());
        return problem;
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleGeneralException(Exception ex) {
        // El error interno se registra con el esquema estructurado y con su
        // traza. La respuesta al cliente no cambia: sigue siendo generica.
        com.erp.integrationhub.application.logging.SecurityEventLogger.registrar(
                "error_interno", null, null, null, "fallo",
                java.util.Map.of("error", ex.getClass().getSimpleName()));
        log.error("Unhandled internal server error: ", ex);
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, "An internal server error occurred");
        problem.setTitle("Internal Server Error");
        problem.setType(URI.create("https://erp-automotive.pe/errors/internal-server-error"));
        problem.setProperty("timestamp", Instant.now());
        return problem;
    }
}
