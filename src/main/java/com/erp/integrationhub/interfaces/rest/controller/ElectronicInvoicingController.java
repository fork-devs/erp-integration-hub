package com.erp.integrationhub.interfaces.rest.controller;

import com.erp.integrationhub.application.dto.command.EmitInvoiceCommand;
import com.erp.integrationhub.application.dto.command.InvoiceItemCommand;
import com.erp.integrationhub.application.dto.result.InvoiceResult;
import com.erp.integrationhub.application.usecase.EmitInvoiceUseCase;
import com.erp.integrationhub.interfaces.rest.request.EmitInvoiceRequest;
import com.erp.integrationhub.interfaces.rest.request.QueryInvoiceStatusRequest;
import com.erp.integrationhub.interfaces.rest.request.VoidInvoiceRequest;
import com.erp.integrationhub.interfaces.rest.response.InvoiceResponse;
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
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * REST controller for electronic invoicing and voucher lifecycle operations (SUNAT compliance).
 */
@RestController
@RequestMapping("/api/v1/integrations/invoices")
@Tag(name = "Electronic Invoicing (SUNAT/APISUNAT)", description = "Endpoints for issuing electronic invoices, tickets, credit notes with SUNAT compliance")
public class ElectronicInvoicingController {

    private final EmitInvoiceUseCase emitInvoiceUseCase;

    public ElectronicInvoicingController(EmitInvoiceUseCase emitInvoiceUseCase) {
        this.emitInvoiceUseCase = emitInvoiceUseCase;
    }

    /**
     * Submits an electronic invoice or voucher to SUNAT via PSE/OSE provider.
     *
     * @param requestedTenantId optional workshop tenant ID header
     * @param idempotencyHeader optional client-supplied idempotency key
     * @param request invoice request details
     * @return invoice response containing CDR status and download links
     */
    @PostMapping("/emit")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN', 'ADMIN_TALLER', 'SUPERADMIN', 'INTERNAL_SERVICE')")
    @Operation(summary = "Emit electronic invoice/boleta to SUNAT via APISUNAT", description = "Submits invoice to SUNAT with deterministic idempotency guarantee and automatic outbox retry on failure")
    public ResponseEntity<InvoiceResponse> emitInvoice(
            @RequestHeader(value = "X-Tenant-ID", required = false) UUID requestedTenantId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyHeader,
            @Valid @RequestBody EmitInvoiceRequest request) {

        UUID tenantId = TenantResolver.resolveForOperation(requestedTenantId, AuthenticatedUserHolder.current());

        String effectiveIdempotencyKey;
        if (idempotencyHeader != null && !idempotencyHeader.isBlank()) {
            effectiveIdempotencyKey = idempotencyHeader.trim();
        } else if (request.invoiceId() != null) {
            effectiveIdempotencyKey = request.invoiceId().toString();
        } else {
            String seed = String.format("invoice:%s:%s:%s:%d",
                    tenantId,
                    request.type(),
                    request.series(),
                    request.number()
            );
            effectiveIdempotencyKey = UUID.nameUUIDFromBytes(seed.getBytes(StandardCharsets.UTF_8)).toString();
        }

        UUID effectiveInvoiceId = request.invoiceId() != null
                ? request.invoiceId()
                : UUID.nameUUIDFromBytes(("comprobante:" + effectiveIdempotencyKey).getBytes(StandardCharsets.UTF_8));

        List<InvoiceItemCommand> itemCommands = request.items().stream()
                .map(i -> new InvoiceItemCommand(
                        i.unitOfMeasure(),
                        i.description(),
                        i.quantity(),
                        i.unitValue(),
                        i.unitPrice(),
                        i.taxAmount(),
                        i.total()
                ))
                .toList();

        EmitInvoiceCommand command = new EmitInvoiceCommand(
                tenantId,
                effectiveInvoiceId,
                request.type(),
                request.series(),
                request.number(),
                request.customerDocType(),
                request.customerDocNumber(),
                request.customerLegalName(),
                request.customerAddress(),
                request.customerEmail(),
                request.currency() != null ? request.currency() : "PEN",
                request.totalTaxable(),
                request.totalTax(),
                request.total(),
                itemCommands,
                request.issueDate(),
                effectiveIdempotencyKey
        );

        InvoiceResult result = withTenantContext(tenantId, () -> emitInvoiceUseCase.emitInvoice(command));
        return ResponseEntity.ok(InvoiceResponse.fromResult(result));
    }

    /**
     * Voids or cancels an electronic invoice or voucher.
     *
     * @param tenantId workshop tenant identifier
     * @param request voiding request details
     * @return cancellation result
     */
    @PostMapping("/void")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN', 'ADMIN_TALLER', 'SUPERADMIN', 'INTERNAL_SERVICE')")
    @Operation(summary = "Void/cancel electronic invoice, note or boleta via APISUNAT", description = "Dispatches communication of voiding (/api/v3/voided) or daily summary (/api/v3/daily-summary) to SUNAT")
    public ResponseEntity<InvoiceResponse> voidInvoice(
            @RequestHeader("X-Tenant-ID") UUID tenantId,
            @Valid @RequestBody VoidInvoiceRequest request) {

        UUID invoiceId = request.invoiceId() != null
                ? request.invoiceId()
                : UUID.nameUUIDFromBytes(("void:" + tenantId + ":" + request.type() + ":" + request.series() + ":" + request.number()).getBytes(StandardCharsets.UTF_8));

        InvoiceResult result = withTenantContext(tenantId, () -> emitInvoiceUseCase.voidInvoice(
                tenantId,
                invoiceId,
                request.type(),
                request.series(),
                request.number(),
                request.reason()
        ));
        return ResponseEntity.ok(InvoiceResponse.fromResult(result));
    }

    /**
     * Queries current status and CDR/PDF links of a document in SUNAT.
     *
     * @param requestedTenantId optional workshop tenant ID header
     * @param request status query parameters
     * @return current voucher status in SUNAT
     */
    @PostMapping("/status")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN', 'ADMIN_TALLER', 'SUPERADMIN', 'ASESOR', 'MECANICO', 'INTERNAL_SERVICE')")
    @Operation(summary = "Query document status in SUNAT via APISUNAT", description = "Retrieves current status, hash, XML, CDR, and PDF links (/api/v3/status)")
    public ResponseEntity<InvoiceResponse> checkStatus(
            @RequestHeader(value = "X-Tenant-ID", required = false) UUID requestedTenantId,
            @Valid @RequestBody QueryInvoiceStatusRequest request) {

        UUID tenantId = TenantResolver.resolveForOperation(requestedTenantId, AuthenticatedUserHolder.current());

        UUID invoiceId = request.invoiceId() != null
                ? request.invoiceId()
                : UUID.nameUUIDFromBytes(("status:" + tenantId + ":" + request.type() + ":" + request.series() + ":" + request.number()).getBytes(StandardCharsets.UTF_8));

        InvoiceResult result = withTenantContext(tenantId, () -> emitInvoiceUseCase.queryInvoiceStatus(
                tenantId,
                invoiceId,
                request.type(),
                request.series(),
                request.number()
        ));
        return ResponseEntity.ok(InvoiceResponse.fromResult(result));
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
