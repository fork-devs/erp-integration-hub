package com.erp.integrationhub.interfaces.rest.controller;

import com.erp.integrationhub.application.dto.command.ProcesarPagoCommand;
import com.erp.integrationhub.application.dto.result.PagoResult;
import com.erp.integrationhub.application.usecase.ProcesarPagoUseCase;
import com.erp.integrationhub.interfaces.rest.request.ProcesarPagoRequest;
import com.erp.integrationhub.interfaces.rest.response.PagoResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
    @Operation(summary = "Process a credit/debit card payment via Stripe", description = "Executes charge with Stripe token and idempotency lock")
    public ResponseEntity<PagoResponse> processCharge(
            @RequestHeader("X-Tenant-ID") UUID tenantId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyHeader,
            @Valid @RequestBody ProcesarPagoRequest request) {

        String effectiveIdempotencyKey = (idempotencyHeader != null && !idempotencyHeader.isBlank())
                ? idempotencyHeader
                : (request.paymentId() != null ? request.paymentId().toString() : UUID.randomUUID().toString());

        ProcesarPagoCommand command = new ProcesarPagoCommand(
                tenantId,
                request.paymentId() != null ? request.paymentId() : UUID.randomUUID(),
                request.monto(),
                request.moneda() != null ? request.moneda() : "PEN",
                request.tokenTarjeta(),
                request.descripcion(),
                request.clienteEmail(),
                effectiveIdempotencyKey
        );

        PagoResult result = procesarPagoUseCase.procesarPago(command);
        return ResponseEntity.ok(PagoResponse.fromResult(result));
    }
}
