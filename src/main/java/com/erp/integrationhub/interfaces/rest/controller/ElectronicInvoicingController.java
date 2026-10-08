package com.erp.integrationhub.interfaces.rest.controller;

import com.erp.integrationhub.application.dto.command.EmitirComprobanteCommand;
import com.erp.integrationhub.application.dto.command.ItemComprobanteCommand;
import com.erp.integrationhub.application.dto.result.ComprobanteResult;
import com.erp.integrationhub.application.usecase.EmitirComprobanteUseCase;
import com.erp.integrationhub.interfaces.rest.request.EmitirComprobanteRequest;
import com.erp.integrationhub.interfaces.rest.response.ComprobanteResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/integrations/invoices")
@Tag(name = "Electronic Invoicing (SUNAT/Nubefact)", description = "Endpoints for issuing electronic invoices, tickets, credit notes with SUNAT compliance")
public class ElectronicInvoicingController {

    private final EmitirComprobanteUseCase emitirComprobanteUseCase;

    public ElectronicInvoicingController(EmitirComprobanteUseCase emitirComprobanteUseCase) {
        this.emitirComprobanteUseCase = emitirComprobanteUseCase;
    }

    @PostMapping("/emit")
    @Operation(summary = "Emit electronic invoice/boleta to SUNAT via Nubefact", description = "Submits invoice to SUNAT with idempotency guarantee and automatic outbox retry on failure")
    public ResponseEntity<ComprobanteResponse> emitInvoice(
            @RequestHeader("X-Tenant-ID") UUID tenantId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyHeader,
            @Valid @RequestBody EmitirComprobanteRequest request) {

        String effectiveIdempotencyKey = (idempotencyHeader != null && !idempotencyHeader.isBlank())
                ? idempotencyHeader
                : (request.comprobanteId() != null ? request.comprobanteId().toString() : UUID.randomUUID().toString());

        List<ItemComprobanteCommand> itemCommands = request.items().stream()
                .map(i -> new ItemComprobanteCommand(
                        i.unidadMedida(),
                        i.descripcion(),
                        i.cantidad(),
                        i.valorUnitario(),
                        i.precioUnitario(),
                        i.igv(),
                        i.total()
                ))
                .toList();

        EmitirComprobanteCommand command = new EmitirComprobanteCommand(
                tenantId,
                request.comprobanteId() != null ? request.comprobanteId() : UUID.randomUUID(),
                request.tipo(),
                request.serie(),
                request.numero(),
                request.clienteTipoDoc(),
                request.clienteNumDoc(),
                request.clienteDenominacion(),
                request.clienteDireccion(),
                request.clienteEmail(),
                request.moneda() != null ? request.moneda() : "PEN",
                request.totalGravada(),
                request.totalIgv(),
                request.total(),
                itemCommands,
                request.fechaEmision(),
                effectiveIdempotencyKey
        );

        ComprobanteResult result = emitirComprobanteUseCase.emitirComprobante(command);
        return ResponseEntity.ok(ComprobanteResponse.fromResult(result));
    }
}
