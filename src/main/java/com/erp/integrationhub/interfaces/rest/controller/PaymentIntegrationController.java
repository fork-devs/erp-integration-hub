package com.erp.integrationhub.interfaces.rest.controller;

import com.erp.integrationhub.application.dto.command.ProcesarPagoCommand;
import com.erp.integrationhub.application.dto.result.PagoResult;
import com.erp.integrationhub.application.usecase.ProcesarPagoUseCase;
import com.erp.integrationhub.interfaces.rest.request.ProcesarPagoRequest;
import com.erp.integrationhub.interfaces.rest.response.PagoResponse;
import com.erp.integrationhub.interfaces.rest.support.AuthenticatedUserHolder;
import com.erp.integrationhub.interfaces.rest.support.TenantContextScope;
import com.erp.integrationhub.interfaces.rest.support.TenantResolver;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/integrations/payments")
@Tag(name = "Payment Gateway (Stripe)", description = "Endpoints for processing card payments with idempotency guarantee")
public class PaymentIntegrationController {

    private final ProcesarPagoUseCase procesarPagoUseCase;

    public PaymentIntegrationController(ProcesarPagoUseCase procesarPagoUseCase) {
        this.procesarPagoUseCase = procesarPagoUseCase;
    }

    @PostMapping("/charge")
    /**
     * Un cobro mueve dinero: solo lo ejecutan los roles de administracion y
     * los servicios internos. Antes, el endpoint exigia autenticacion, y eso
     * bastaba para que cualquier principalAuthenticated lo ejecutara.
     */
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN', 'ADMIN_TALLER', 'SUPERADMIN', 'INTERNAL_SERVICE')")
    @Operation(summary = "Process a credit/debit card payment via Stripe", description = "Executes charge with Stripe token and deterministic idempotency lock")
    public ResponseEntity<PagoResponse> processCharge(
            @RequestHeader(value = "X-Tenant-ID", required = false) UUID requestedTenantId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyHeader,
            @Valid @RequestBody ProcesarPagoRequest request) {

        UUID tenantId = TenantResolver.resolveForOperation(requestedTenantId, AuthenticatedUserHolder.current());

        // Deterministic Idempotency Key derivation to prevent duplicate card charges
        String effectiveIdempotencyKey;
        if (idempotencyHeader != null && !idempotencyHeader.isBlank()) {
            effectiveIdempotencyKey = idempotencyHeader.trim();
        } else if (request.paymentId() != null) {
            effectiveIdempotencyKey = request.paymentId().toString();
        } else {
            // Deterministic hash based on payment transaction parameters
            String seed = String.format("charge:%s:%s:%.2f:%s",
                    tenantId,
                    request.tokenTarjeta(),
                    request.monto() != null ? request.monto().doubleValue() : 0.0,
                    request.clienteEmail() != null ? request.clienteEmail().trim().toLowerCase() : ""
            );
            effectiveIdempotencyKey = UUID.nameUUIDFromBytes(seed.getBytes(StandardCharsets.UTF_8)).toString();
        }

        UUID effectivePaymentId = request.paymentId() != null
                ? request.paymentId()
                : UUID.nameUUIDFromBytes(("payment:" + effectiveIdempotencyKey).getBytes(StandardCharsets.UTF_8));

        ProcesarPagoCommand command = new ProcesarPagoCommand(
                tenantId,
                effectivePaymentId,
                request.monto(),
                request.moneda() != null ? request.moneda() : "PEN",
                request.tokenTarjeta(),
                request.descripcion(),
                request.clienteEmail(),
                effectiveIdempotencyKey
        );

        PagoResult result = enContextoDeTaller(tenantId, () -> procesarPagoUseCase.procesarPago(command));
        return ResponseEntity.ok(PagoResponse.fromResult(result));
    }

    /**
     * Ejecuta la operacion con el contexto de taller establecido, de modo que
     * la politica de Row-Level Security filtre por el taller del principal y
     * no por el de la fila.
     */
    private <T> T enContextoDeTaller(UUID tenantId, java.util.function.Supplier<T> operacion) {
        try (TenantContextScope scope = TenantContextScope.forTenant(tenantId)) {
            return operacion.get();
        }
    }

}
