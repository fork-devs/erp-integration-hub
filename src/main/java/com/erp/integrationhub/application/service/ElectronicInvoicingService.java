package com.erp.integrationhub.application.service;

import com.erp.integrationhub.application.dto.command.EmitInvoiceCommand;
import com.erp.integrationhub.application.dto.command.InvoiceItemCommand;
import com.erp.integrationhub.application.dto.result.InvoiceResult;
import com.erp.integrationhub.application.usecase.EmitInvoiceUseCase;
import com.erp.integrationhub.domain.exception.IdempotencyConflictException;
import com.erp.integrationhub.domain.model.Currency;
import com.erp.integrationhub.domain.model.DeclarationResult;
import com.erp.integrationhub.domain.model.IdempotencyRecord;
import com.erp.integrationhub.domain.model.IdempotencyScope;
import com.erp.integrationhub.domain.model.IntegrationAttempt;
import com.erp.integrationhub.domain.model.InvoiceItem;
import com.erp.integrationhub.domain.model.InvoiceStatus;
import com.erp.integrationhub.domain.model.InvoiceToDeclare;
import com.erp.integrationhub.domain.model.InvoiceType;
import com.erp.integrationhub.domain.model.ProviderType;
import com.erp.integrationhub.domain.model.RetryTask;
import com.erp.integrationhub.domain.port.IdempotencyRepositoryPort;
import com.erp.integrationhub.domain.port.IntegrationAttemptRepositoryPort;
import com.erp.integrationhub.domain.port.RetryQueueRepositoryPort;
import com.erp.integrationhub.domain.port.TaxAuthorityEmitterPort;
import com.erp.integrationhub.domain.port.UnitOfWork;
import com.erp.integrationhub.domain.port.VoucherNumberingPort;
import com.erp.integrationhub.domain.util.PayloadFingerprint;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Application service managing electronic invoice lifecycle, SUNAT declarations, and retry outbox queue.
 */
public class ElectronicInvoicingService implements EmitInvoiceUseCase {

    /** Maximum retry attempts for queued invoice operations covering ~24-hour backoff window. */
    public static final int MAX_RETRY_ATTEMPTS = 7;
    /** Backward compatibility alias for MAX_RETRY_ATTEMPTS. */
    public static final int MAX_INTENTOS_REINTENTO = MAX_RETRY_ATTEMPTS;

    private static final Logger log = LoggerFactory.getLogger(ElectronicInvoicingService.class);

    private final TaxAuthorityEmitterPort taxAuthorityEmitterPort;
    private final IdempotencyRepositoryPort idempotencyRepositoryPort;
    private final IntegrationAttemptRepositoryPort attemptRepositoryPort;
    private final RetryQueueRepositoryPort retryQueueRepositoryPort;
    private final ObjectMapper objectMapper;
    private final UnitOfWork unitOfWork;
    private final VoucherNumberingPort voucherNumberingPort;

    public ElectronicInvoicingService(
            TaxAuthorityEmitterPort taxAuthorityEmitterPort,
            IdempotencyRepositoryPort idempotencyRepositoryPort,
            IntegrationAttemptRepositoryPort attemptRepositoryPort,
            RetryQueueRepositoryPort retryQueueRepositoryPort,
            UnitOfWork unitOfWork,
            VoucherNumberingPort voucherNumberingPort) {
        this.taxAuthorityEmitterPort = taxAuthorityEmitterPort;
        this.idempotencyRepositoryPort = idempotencyRepositoryPort;
        this.attemptRepositoryPort = attemptRepositoryPort;
        this.retryQueueRepositoryPort = retryQueueRepositoryPort;
        this.objectMapper = new ObjectMapper().findAndRegisterModules();
        this.unitOfWork = unitOfWork;
        this.voucherNumberingPort = voucherNumberingPort;
    }

    @Override
    public InvoiceResult emitInvoice(EmitInvoiceCommand command) {
        if (command.tenantId() == null || command.invoiceId() == null) {
            throw new IllegalArgumentException("tenantId and invoiceId are required");
        }

        // 1. Request payload fingerprint: distinguishes identical retry from different emission sharing key
        String idempotencyKey = command.idempotencyKey() != null ? command.idempotencyKey().trim() : command.invoiceId().toString();
        String fingerprint = PayloadFingerprint.of(buildPayloadFingerprintSource(command));

        // 2. Check existing idempotency record
        Optional<IdempotencyRecord> existingRecord = idempotencyRepositoryPort.findByTenantAndScopeAndKey(
                command.tenantId(), IdempotencyScope.VOUCHER, idempotencyKey
        );

        if (existingRecord.isPresent()) {
            IdempotencyRecord record = existingRecord.get();

            if (!record.isSameRequest(fingerprint) && record.getState().isDefinitiveResult()) {
                throw new IdempotencyConflictException(
                        "Idempotency key has already been used with a different request payload");
            }

            InvoiceResult cachedResult = deserializeResult(command.invoiceId(), record.getResponsePayload(), true);

            // If pending in SUNAT, re-check provider status
            if (InvoiceStatus.PENDING.name().equalsIgnoreCase(cachedResult.sunatStatus())
                    || InvoiceStatus.PENDIENTE_SUNAT_ALIAS.equalsIgnoreCase(cachedResult.sunatStatus())) {
                try {
                    DeclarationResult statusCheck = taxAuthorityEmitterPort.queryInvoiceStatus(
                            command.type(), command.series(), command.number()
                    );
                    if (statusCheck.successful() && statusCheck.status().isIssued()) {
                        InvoiceResult updatedResult = new InvoiceResult(
                                command.invoiceId(),
                                true,
                                statusCheck.sunatStatus(),
                                statusCheck.sunatResponseCode(),
                                statusCheck.sunatMessage(),
                                statusCheck.pdfUrl(),
                                statusCheck.xmlUrl(),
                                statusCheck.cdrUrl(),
                                statusCheck.cdrHash(),
                                false
                        );
                        unitOfWork.runInTransaction(() -> idempotencyRepositoryPort.updateByKey(
                                command.tenantId(),
                                IdempotencyScope.VOUCHER,
                                idempotencyKey,
                                record.completed(serializeResult(updatedResult), null)));
                        return updatedResult;
                    }
                } catch (Exception ignored) {}
            }

            log.info("Idempotent request detected for invoice {}. Returning cached result.", command.invoiceId());
            return cachedResult;
        }

        // 2. Map command to agnostic domain model
        InvoiceType type = InvoiceType.valueOf(command.type().toUpperCase().trim());
        Currency currency = "USD".equalsIgnoreCase(command.currency()) ? Currency.USD : Currency.PEN;

        List<InvoiceItem> items = command.items().stream()
                .map(this::toItemModel)
                .collect(Collectors.toList());

        InvoiceToDeclare invoice = new InvoiceToDeclare(
                command.tenantId(),
                command.invoiceId(),
                type,
                command.series(),
                command.number(),
                command.customerDocType(),
                command.customerDocNumber(),
                command.customerLegalName(),
                command.customerAddress(),
                command.customerEmail(),
                currency,
                command.totalTaxable(),
                command.totalTax(),
                command.total(),
                items,
                command.issueDate()
        );

        // 2 bis. Reserve sequential numbering BEFORE contacting external tax authority
        unitOfWork.runInTransaction(() -> voucherNumberingPort.reserve(
                command.tenantId(), command.series(), command.number(), command.invoiceId()));

        // 3. Declaration towards PSE / SUNAT with outbox fault tolerance
        DeclarationResult declarationResult;
        boolean emitted = false;
        try {
            declarationResult = taxAuthorityEmitterPort.emitInvoice(invoice);
            emitted = true;
        } catch (Exception e) {
            log.warn("Communication failure with PSE/SUNAT ({}: {}). Enqueuing to retry outbox.", command.invoiceId(), e.getMessage());

            unitOfWork.runInTransaction(() -> {
                int attemptCount = 1 + attemptRepositoryPort.countAttempts(
                        command.tenantId(), ProviderType.APISUNAT, "EMIT_VOUCHER", command.invoiceId());

                attemptRepositoryPort.save(IntegrationAttempt.retry(
                        command.tenantId(),
                        ProviderType.APISUNAT,
                        "EMIT_VOUCHER",
                        command.invoiceId(),
                        attemptCount,
                        e.getMessage()
                ));

                retryQueueRepositoryPort.save(RetryTask.create(
                        command.tenantId(),
                        ProviderType.APISUNAT,
                        "EMIT_VOUCHER",
                        command.invoiceId(),
                        serializeCommand(command),
                        MAX_RETRY_ATTEMPTS
                ));
            });

            declarationResult = DeclarationResult.pending("Tax authority temporarily unavailable. Invoice enqueued for automatic retry.");
        }

        if (emitted) {
            unitOfWork.runInTransaction(() -> {
                int attemptCount = 1 + attemptRepositoryPort.countAttempts(
                        command.tenantId(), ProviderType.APISUNAT, "EMIT_VOUCHER", command.invoiceId());
                attemptRepositoryPort.save(IntegrationAttempt.ok(
                        command.tenantId(),
                        ProviderType.APISUNAT,
                        "EMIT_VOUCHER",
                        command.invoiceId(),
                        attemptCount
                ));
            });
        }

        // 4. Store idempotency record
        InvoiceResult finalResult = new InvoiceResult(
                command.invoiceId(),
                declarationResult.successful(),
                declarationResult.sunatStatus(),
                declarationResult.sunatResponseCode(),
                declarationResult.sunatMessage(),
                declarationResult.pdfUrl(),
                declarationResult.xmlUrl(),
                declarationResult.cdrUrl(),
                declarationResult.cdrHash(),
                false
        );

        unitOfWork.runInTransaction(() -> idempotencyRepositoryPort.save(IdempotencyRecord.of(
                command.tenantId(),
                IdempotencyScope.VOUCHER,
                idempotencyKey,
                serializeResult(finalResult)
        )));

        return finalResult;
    }

    @Override
    public InvoiceResult voidInvoice(UUID tenantId, UUID invoiceId, String type, String series, int number, String reason) {
        if (tenantId == null || invoiceId == null) {
            throw new IllegalArgumentException("tenantId and invoiceId are required");
        }
        log.info("[APISUNAT] Voiding invoice {} {}-{} for tenant {}", type, series, number, tenantId);

        DeclarationResult declarationResult;
        try {
            declarationResult = taxAuthorityEmitterPort.voidInvoice(type, series, number, reason);
            unitOfWork.runInTransaction(() -> attemptRepositoryPort.save(IntegrationAttempt.ok(
                    tenantId,
                    ProviderType.APISUNAT,
                    "VOID_VOUCHER",
                    invoiceId,
                    1
            )));
        } catch (Exception e) {
            log.warn("Error voiding voucher in APISUNAT ({}): {}", invoiceId, e.getMessage());

            unitOfWork.runInTransaction(() -> {
                int attemptCount = 1 + attemptRepositoryPort.countAttempts(
                        tenantId, ProviderType.APISUNAT, "VOID_VOUCHER", invoiceId);

                attemptRepositoryPort.save(IntegrationAttempt.retry(
                        tenantId,
                        ProviderType.APISUNAT,
                        "VOID_VOUCHER",
                        invoiceId,
                        attemptCount,
                        e.getMessage()));

                retryQueueRepositoryPort.save(RetryTask.create(
                        tenantId,
                        ProviderType.APISUNAT,
                        "VOID_VOUCHER",
                        invoiceId,
                        "{\"tipo\":\"" + type + "\",\"serie\":\"" + series + "\",\"numero\":" + number
                                + ",\"motivo\":\"" + (reason != null ? reason : "") + "\"}",
                        5));
            });

            declarationResult = DeclarationResult.pending(
                    "Tax service temporarily unavailable: voiding operation enqueued for retry");
        }

        return new InvoiceResult(
                invoiceId,
                declarationResult.successful(),
                declarationResult.sunatStatus(),
                declarationResult.sunatResponseCode(),
                declarationResult.sunatMessage(),
                declarationResult.pdfUrl(),
                declarationResult.xmlUrl(),
                declarationResult.cdrUrl(),
                declarationResult.cdrHash(),
                false
        );
    }

    @Override
    public InvoiceResult queryInvoiceStatus(UUID tenantId, UUID invoiceId, String type, String series, int number) {
        if (tenantId == null || invoiceId == null) {
            throw new IllegalArgumentException("tenantId and invoiceId are required");
        }
        log.info("[APISUNAT] Querying voucher status {} {}-{} for tenant {}", type, series, number, tenantId);

        DeclarationResult declarationResult;
        try {
            declarationResult = taxAuthorityEmitterPort.queryInvoiceStatus(type, series, number);
        } catch (Exception e) {
            log.warn("Error querying voucher status in APISUNAT ({}): {}", invoiceId, e.getMessage());
            declarationResult = DeclarationResult.rejected("500", "Failed to query status with APISUNAT: " + e.getMessage());
        }

        return new InvoiceResult(
                invoiceId,
                declarationResult.successful(),
                declarationResult.sunatStatus(),
                declarationResult.sunatResponseCode(),
                declarationResult.sunatMessage(),
                declarationResult.pdfUrl(),
                declarationResult.xmlUrl(),
                declarationResult.cdrUrl(),
                declarationResult.cdrHash(),
                false
        );
    }

    private InvoiceItem toItemModel(InvoiceItemCommand cmd) {
        return new InvoiceItem(
                cmd.unitOfMeasure(),
                cmd.description(),
                cmd.quantity(),
                cmd.unitValue(),
                cmd.unitPrice(),
                cmd.taxAmount(),
                cmd.total()
        );
    }

    private String buildPayloadFingerprintSource(EmitInvoiceCommand command) {
        return String.join("|",
                String.valueOf(command.tenantId()),
                String.valueOf(command.invoiceId()),
                String.valueOf(command.type()),
                String.valueOf(command.series()),
                String.valueOf(command.number()),
                String.valueOf(command.customerDocNumber()),
                String.valueOf(command.total()),
                String.valueOf(command.currency()));
    }

    private String serializeResult(InvoiceResult result) {
        try {
            return objectMapper.writeValueAsString(result);
        } catch (Exception e) {
            throw new RuntimeException("Error serializing invoice result", e);
        }
    }

    private InvoiceResult deserializeResult(UUID invoiceId, String payload, boolean fromCache) {
        if (payload == null || payload.isBlank()) {
            return new InvoiceResult(invoiceId, true, "PENDIENTE_SUNAT", "0", "Processing", null, null, null, null, fromCache);
        }
        try {
            com.fasterxml.jackson.databind.JsonNode root = objectMapper.readTree(payload);
            boolean successful = root.path("successful").asBoolean(root.path("exitoso").asBoolean(true));
            String status = root.path("sunatStatus").asText(root.path("estadoSunat").asText(root.path("estado").asText("ACEPTADA")));
            String code = root.path("sunatResponseCode").asText(root.path("codigoRespuestaSunat").asText(root.path("codigo").asText("0")));
            String message = root.path("sunatMessage").asText(root.path("mensajeSunat").asText(root.path("mensaje").asText("Comprobante procesado")));
            String pdfUrl = root.path("pdfUrl").asText(null);
            String xmlUrl = root.path("xmlUrl").asText(null);
            String cdrUrl = root.path("cdrUrl").asText(null);
            String cdrHash = root.path("cdrHash").asText(root.path("hashCdr").asText(null));

            return new InvoiceResult(
                    invoiceId,
                    successful,
                    status,
                    code,
                    message,
                    pdfUrl,
                    xmlUrl,
                    cdrUrl,
                    cdrHash,
                    fromCache
            );
        } catch (Exception e) {
            log.warn("Error deserializing cached invoice result: {}", e.getMessage());
            return new InvoiceResult(invoiceId, true, "PENDIENTE_SUNAT", "0", "Processing", null, null, null, null, fromCache);
        }
    }

    private String serializeCommand(EmitInvoiceCommand cmd) {
        try {
            return objectMapper.writeValueAsString(cmd);
        } catch (Exception e) {
            throw new RuntimeException("Error serializing command for retry outbox", e);
        }
    }
}
