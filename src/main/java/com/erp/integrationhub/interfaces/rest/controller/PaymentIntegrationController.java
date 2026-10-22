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
            @RequestHeader(value = "Idempotency-Key") String idempotencyHeader,
            @Valid @RequestBody ProcesarPagoRequest request) {

        UUID tenantId = TenantResolver.resolveForOperation(requestedTenantId, AuthenticatedUserHolder.current());

        // La clave de idempotencia es OBLIGATORIA, y la aporta el cliente.
        //
        // Antes se derivaba de los datos del cobro. Con la derivacion, la
        // clave era la misma para dos cobros identicos del mismo cliente, de
        // modo que el segundo se tomato por una repeticion del primero y NO
        // se cobraba: el cliente pagaba una vez por dos servicios. La clave
        // tiene que distinguir una peticion de otra, y solo el cliente puede
        // hacerlo.
        if (idempotencyHeader == null || idempotencyHeader.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "La cabecera Idempotency-Key es obligatoria para procesar un cobro");
        }
        String effectiveIdempotencyKey = idempotencyHeader.trim();

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
