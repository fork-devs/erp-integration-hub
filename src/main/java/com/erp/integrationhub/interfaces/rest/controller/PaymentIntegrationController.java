package com.erp.integrationhub.interfaces.rest.controller;

import com.erp.integrationhub.application.dto.command.ProcessPaymentCommand;
import com.erp.integrationhub.application.dto.result.PaymentResult;
import com.erp.integrationhub.application.usecase.ProcessPaymentUseCase;
import com.erp.integrationhub.interfaces.rest.request.ProcessPaymentRequest;
import com.erp.integrationhub.interfaces.rest.response.PaymentResponse;
import com.erp.integrationhub.interfaces.rest.support.AuthenticatedUserHolder;
import com.erp.integrationhub.interfaces.rest.support.TenantContextScope;
import com.erp.integrationhub.interfaces.rest.support.TenantResolver;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * REST controller for processing payment gateway transactions (Stripe).
 */
@RestController
@RequestMapping("/api/v1/integrations/payments")
@Tag(name = "Payment Gateway (Stripe)", description = "Endpoints for processing card payments with idempotency guarantee")
public class PaymentIntegrationController {

    private final ProcessPaymentUseCase processPaymentUseCase;

    public PaymentIntegrationController(ProcessPaymentUseCase processPaymentUseCase) {
        this.processPaymentUseCase = processPaymentUseCase;
    }

    /**
     * Processes a credit or debit card charge through Stripe with mandatory client idempotency key.
     *
     * @param requestedTenantId optional workshop tenant ID header
     * @param idempotencyHeader mandatory client idempotency key header
     * @param request payment details (amount, currency, card token, description)
     * @return payment outcome details
     */
    @PostMapping("/charge")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN', 'ADMIN_TALLER', 'SUPERADMIN', 'INTERNAL_SERVICE')")
    @Operation(summary = "Process a credit/debit card payment via Stripe", description = "Executes charge with Stripe token and deterministic idempotency lock")
    public ResponseEntity<PaymentResponse> processCharge(
            @RequestHeader(value = "X-Tenant-ID", required = false) UUID requestedTenantId,
            @RequestHeader(value = "Idempotency-Key") String idempotencyHeader,
            @Valid @RequestBody ProcessPaymentRequest request) {

        UUID tenantId = TenantResolver.resolveForOperation(requestedTenantId, AuthenticatedUserHolder.current());

        if (idempotencyHeader == null || idempotencyHeader.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Idempotency-Key header is mandatory for processing payments");
        }
        String effectiveIdempotencyKey = idempotencyHeader.trim();

        UUID effectivePaymentId = request.paymentId() != null
                ? request.paymentId()
                : UUID.nameUUIDFromBytes(("payment:" + effectiveIdempotencyKey).getBytes(StandardCharsets.UTF_8));

        ProcessPaymentCommand command = new ProcessPaymentCommand(
                tenantId,
                effectivePaymentId,
                request.amount(),
                request.currency() != null ? request.currency() : "PEN",
                request.cardToken(),
                request.description(),
                request.customerEmail(),
                effectiveIdempotencyKey
        );

        PaymentResult result = withTenantContext(tenantId, () -> processPaymentUseCase.processPayment(command));
        return ResponseEntity.ok(PaymentResponse.fromResult(result));
    }

    /**
     * Executes the given operation within the workshop tenant context for proper RLS filtering.
     */
    private <T> T withTenantContext(UUID tenantId, Supplier<T> operation) {
        try (TenantContextScope scope = TenantContextScope.forTenant(tenantId)) {
            return operation.get();
        }
    }
}
