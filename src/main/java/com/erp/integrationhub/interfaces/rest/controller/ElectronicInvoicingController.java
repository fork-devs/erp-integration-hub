package com.erp.integrationhub.interfaces.rest.controller;

import com.erp.integrationhub.application.dto.command.EmitirComprobanteCommand;
import com.erp.integrationhub.application.dto.command.ItemComprobanteCommand;
import com.erp.integrationhub.application.dto.result.ComprobanteResult;
import com.erp.integrationhub.application.usecase.EmitirComprobanteUseCase;
import com.erp.integrationhub.interfaces.rest.request.AnularComprobanteRequest;
import com.erp.integrationhub.interfaces.rest.request.ConsultarEstadoComprobanteRequest;
import com.erp.integrationhub.interfaces.rest.request.EmitirComprobanteRequest;
import com.erp.integrationhub.interfaces.rest.response.ComprobanteResponse;
import com.erp.integrationhub.interfaces.rest.support.AuthenticatedUserHolder;
import com.erp.integrationhub.interfaces.rest.support.TenantResolver;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/integrations/invoices")
@Tag(name = "Electronic Invoicing (SUNAT/APISUNAT)", description = "Endpoints for issuing electronic invoices, tickets, credit notes with SUNAT compliance")
public class ElectronicInvoicingController {

    private final EmitirComprobanteUseCase emitirComprobanteUseCase;

    public ElectronicInvoicingController(EmitirComprobanteUseCase emitirComprobanteUseCase) {
        this.emitirComprobanteUseCase = emitirComprobanteUseCase;
    }

    @PostMapping("/emit")
    @Operation(summary = "Emit electronic invoice/boleta to SUNAT via APISUNAT", description = "Submits invoice to SUNAT with deterministic idempotency guarantee and automatic outbox retry on failure")
    public ResponseEntity<ComprobanteResponse> emitInvoice(
            @RequestHeader(value = "X-Tenant-ID", required = false) UUID requestedTenantId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyHeader,
            @Valid @RequestBody EmitirComprobanteRequest request) {

        UUID tenantId = TenantResolver.resolveForOperation(requestedTenantId, AuthenticatedUserHolder.current());

        String effectiveIdempotencyKey;
        if (idempotencyHeader != null && !idempotencyHeader.isBlank()) {
            effectiveIdempotencyKey = idempotencyHeader.trim();
        } else if (request.comprobanteId() != null) {
            effectiveIdempotencyKey = request.comprobanteId().toString();
        } else {
            String seed = String.format("invoice:%s:%s:%s:%d",
                    tenantId,
                    request.tipo(),
                    request.serie(),
                    request.numero()
            );
            effectiveIdempotencyKey = UUID.nameUUIDFromBytes(seed.getBytes(StandardCharsets.UTF_8)).toString();
        }

        UUID effectiveComprobanteId = request.comprobanteId() != null
                ? request.comprobanteId()
                : UUID.nameUUIDFromBytes(("comprobante:" + effectiveIdempotencyKey).getBytes(StandardCharsets.UTF_8));

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
                effectiveComprobanteId,
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

    @PostMapping("/void")
    @Operation(summary = "Void/cancel electronic invoice, note or boleta via APISUNAT", description = "Dispatches communication of voiding (/api/v3/voided) or daily summary (/api/v3/daily-summary) to SUNAT")
    public ResponseEntity<ComprobanteResponse> voidInvoice(
            @RequestHeader("X-Tenant-ID") UUID tenantId,
            @Valid @RequestBody AnularComprobanteRequest request) {

        UUID compId = request.comprobanteId() != null
                ? request.comprobanteId()
                : UUID.nameUUIDFromBytes(("void:" + tenantId + ":" + request.tipo() + ":" + request.serie() + ":" + request.numero()).getBytes(StandardCharsets.UTF_8));

        ComprobanteResult result = emitirComprobanteUseCase.anularComprobante(
                tenantId,
                compId,
                request.tipo(),
                request.serie(),
                request.numero(),
                request.motivo()
        );
        return ResponseEntity.ok(ComprobanteResponse.fromResult(result));
    }

    @PostMapping("/status")
    @Operation(summary = "Query document status in SUNAT via APISUNAT", description = "Retrieves current status, hash, XML, CDR, and PDF links (/api/v3/status)")
    public ResponseEntity<ComprobanteResponse> checkStatus(
            @RequestHeader("X-Tenant-ID") UUID tenantId,
            @Valid @RequestBody ConsultarEstadoComprobanteRequest request) {

        UUID compId = request.comprobanteId() != null
                ? request.comprobanteId()
                : UUID.nameUUIDFromBytes(("status:" + tenantId + ":" + request.tipo() + ":" + request.serie() + ":" + request.numero()).getBytes(StandardCharsets.UTF_8));

        ComprobanteResult result = emitirComprobanteUseCase.consultarEstadoComprobante(
                tenantId,
                compId,
                request.tipo(),
                request.serie(),
                request.numero()
        );
        return ResponseEntity.ok(ComprobanteResponse.fromResult(result));
    }
}
