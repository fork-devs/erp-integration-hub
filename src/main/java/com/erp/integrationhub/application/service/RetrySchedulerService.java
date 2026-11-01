package com.erp.integrationhub.application.service;

import com.erp.integrationhub.application.dto.command.EmitInvoiceCommand;
import com.erp.integrationhub.application.dto.result.InvoiceResult;
import com.erp.integrationhub.application.usecase.ProcessRetryQueueUseCase;
import com.erp.integrationhub.domain.model.Currency;
import com.erp.integrationhub.domain.model.DeclarationResult;
import com.erp.integrationhub.domain.model.IdempotencyRecord;
import com.erp.integrationhub.domain.model.IdempotencyScope;
import com.erp.integrationhub.domain.model.IntegrationAttempt;
import com.erp.integrationhub.domain.model.InvoiceItem;
import com.erp.integrationhub.domain.model.InvoiceToDeclare;
import com.erp.integrationhub.domain.model.InvoiceType;
import com.erp.integrationhub.domain.model.RetryTask;
import com.erp.integrationhub.domain.port.IdempotencyRepositoryPort;
import com.erp.integrationhub.domain.port.IntegrationAttemptRepositoryPort;
import com.erp.integrationhub.domain.port.PaymentGatewayPort;
import com.erp.integrationhub.domain.port.RetryQueueRepositoryPort;
import com.erp.integrationhub.domain.port.TaxAuthorityEmitterPort;
import com.erp.integrationhub.domain.port.UnitOfWork;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Background worker service processing pending retries from the outbox retry queue.
 */
public class RetrySchedulerService implements ProcessRetryQueueUseCase {

    /** Maximum retry attempts for queued tasks covering backoff window. */
    public static final int MAX_RETRY_ATTEMPTS = 7;
    /** Backward compatibility alias for MAX_RETRY_ATTEMPTS. */
    public static final int MAX_INTENTOS_REINTENTO = MAX_RETRY_ATTEMPTS;

    private static final Logger log = LoggerFactory.getLogger(RetrySchedulerService.class);

    private final RetryQueueRepositoryPort retryQueueRepositoryPort;
    private final IntegrationAttemptRepositoryPort attemptRepositoryPort;
    private final IdempotencyRepositoryPort idempotencyRepositoryPort;
    private final TaxAuthorityEmitterPort taxAuthorityEmitterPort;
    private final PaymentGatewayPort paymentGatewayPort;
    private final ObjectMapper objectMapper;
    private final UnitOfWork unitOfWork;

    public RetrySchedulerService(
            RetryQueueRepositoryPort retryQueueRepositoryPort,
            IntegrationAttemptRepositoryPort attemptRepositoryPort,
            IdempotencyRepositoryPort idempotencyRepositoryPort,
            TaxAuthorityEmitterPort taxAuthorityEmitterPort,
            PaymentGatewayPort paymentGatewayPort,
            UnitOfWork unitOfWork) {
        this.retryQueueRepositoryPort = retryQueueRepositoryPort;
        this.attemptRepositoryPort = attemptRepositoryPort;
        this.idempotencyRepositoryPort = idempotencyRepositoryPort;
        this.taxAuthorityEmitterPort = taxAuthorityEmitterPort;
        this.paymentGatewayPort = paymentGatewayPort;
        this.objectMapper = new ObjectMapper().findAndRegisterModules();
        this.unitOfWork = unitOfWork;
    }

    @Override
    public int processPendingRetries(int batchSize) {
        List<RetryTask> pendingTasks = retryQueueRepositoryPort.findPendingTasks(Instant.now(), batchSize);
        int processed = 0;

        for (RetryTask task : pendingTasks) {
            try {
                log.info("Executing retry for task {} with provider {} (Attempt {}/{})",
                        task.getId(), task.getProvider(), task.getAttemptsCount() + 1, task.getMaxAttempts());

                if ("EMIT_VOUCHER".equalsIgnoreCase(task.getOperation())) {
                    executeVoucherRetry(task);
                } else if ("PROCESS_PAYMENT".equalsIgnoreCase(task.getOperation())) {
                    executePaymentRetry(task);
                } else {
                    log.warn("Unknown operation in retry task: {}", task.getOperation());
                    unitOfWork.runInTransaction(() -> {
                        task.markCompleted();
                        retryQueueRepositoryPort.save(task);
                    });
                }

                processed++;
            } catch (Exception e) {
                log.warn("Failed to retry task {}: {}", task.getId(), e.getMessage());
                unitOfWork.runInTransaction(() -> {
                    task.recordFailure(e.getMessage());
                    retryQueueRepositoryPort.save(task);

                    boolean exhausted = "DLQ".equals(task.getStatus());
                    IntegrationAttempt attempt = exhausted
                            ? IntegrationAttempt.dlq(
                                    task.getTenantId(),
                                    task.getProvider(),
                                    task.getOperation(),
                                    task.getReferenceId(),
                                    task.getAttemptsCount(),
                                    e.getMessage())
                            : IntegrationAttempt.retry(
                                    task.getTenantId(),
                                    task.getProvider(),
                                    task.getOperation(),
                                    task.getReferenceId(),
                                    task.getAttemptsCount(),
                                    e.getMessage()
                            );

                    if (attemptRepositoryPort instanceof com.erp.integrationhub.infrastructure.persistence.adapter
                            .IntegrationAttemptPersistenceAdapter adapter) {
                        adapter.saveWithPlatformScope(attempt);
                    } else {
                        attemptRepositoryPort.save(attempt);
                    }
                });
            }
        }

        return processed;
    }

    private void updateIdempotency(EmitInvoiceCommand cmd, String payload) {
        String key = cmd.idempotencyKey() != null ? cmd.idempotencyKey() : cmd.invoiceId().toString();
        IdempotencyRecord existing = idempotencyRepositoryPort
                .findByTenantAndScopeAndKey(cmd.tenantId(), IdempotencyScope.VOUCHER, key)
                .orElse(null);
        if (existing != null) {
            idempotencyRepositoryPort.updateByKey(cmd.tenantId(), IdempotencyScope.VOUCHER, key,
                    existing.completed(payload, null));
            return;
        }
        IdempotencyRecord claimed = idempotencyRepositoryPort
                .claim(cmd.tenantId(), IdempotencyScope.VOUCHER, key, null);
        idempotencyRepositoryPort.updateByKey(cmd.tenantId(), IdempotencyScope.VOUCHER, key,
                claimed.completed(payload, null));
    }

    private void executeVoucherRetry(RetryTask task) throws Exception {
        EmitInvoiceCommand cmd = objectMapper.readValue(task.getPayload(), EmitInvoiceCommand.class);

        InvoiceType type = InvoiceType.valueOf(cmd.type().toUpperCase().trim());
        Currency currency = "USD".equalsIgnoreCase(cmd.currency()) ? Currency.USD : Currency.PEN;

        List<InvoiceItem> items = cmd.items().stream()
                .map(i -> new InvoiceItem(
                        i.unitOfMeasure(),
                        i.description(),
                        i.quantity(),
                        i.unitValue(),
                        i.unitPrice(),
                        i.taxAmount(),
                        i.total()
                ))
                .collect(Collectors.toList());

        InvoiceToDeclare invoice = new InvoiceToDeclare(
                cmd.tenantId(),
                cmd.invoiceId(),
                type,
                cmd.series(),
                cmd.number(),
                cmd.customerDocType(),
                cmd.customerDocNumber(),
                cmd.customerLegalName(),
                cmd.customerAddress(),
                cmd.customerEmail(),
                currency,
                cmd.totalTaxable(),
                cmd.totalTax(),
                cmd.total(),
                items,
                cmd.issueDate()
        );

        DeclarationResult result = taxAuthorityEmitterPort.emitInvoice(invoice);

        if (!result.successful()) {
            throw new RuntimeException("Unsuccessful response from APISUNAT/SUNAT: " + result.sunatMessage());
        }

        InvoiceResult finalResult = new InvoiceResult(
                cmd.invoiceId(),
                true,
                result.sunatStatus(),
                result.sunatResponseCode(),
                result.sunatMessage(),
                result.pdfUrl(),
                result.xmlUrl(),
                result.cdrUrl(),
                result.cdrHash(),
                false
        );

        final String idempotencyPayload;
        try {
            idempotencyPayload = objectMapper.writeValueAsString(finalResult);
        } catch (Exception e) {
            throw new RuntimeException("Could not serialize invoice result", e);
        }

        try {
            unitOfWork.runInTransaction(() -> {
                task.markCompleted();
                retryQueueRepositoryPort.save(task);

                try {
                    updateIdempotency(cmd, idempotencyPayload);
                } catch (RuntimeException e) {
                    log.error("Failed to update idempotency cache for invoice {}: {}",
                            cmd.invoiceId(), e.getMessage(), e);
                }

                attemptRepositoryPort.save(IntegrationAttempt.ok(
                        task.getTenantId(),
                        task.getProvider(),
                        task.getOperation(),
                        task.getReferenceId(),
                        task.getAttemptsCount() + 1
                ));
            });
        } catch (Exception e) {
            log.error("Invoice {} was issued by provider but failed local registration: {}",
                    cmd.invoiceId(), e.getMessage());

            unitOfWork.runInTransaction(() -> {
                task.markDeadLetter("Invoice emitted by provider, but failed local persistence: "
                        + e.getMessage());
                retryQueueRepositoryPort.save(task);

                attemptRepositoryPort.save(IntegrationAttempt.retry(
                        task.getTenantId(),
                        task.getProvider(),
                        task.getOperation(),
                        task.getReferenceId(),
                        task.getAttemptsCount() + 1,
                        "Invoice emitted by provider, but failed local persistence: " + e.getMessage()
                ));
            });
        }
    }

    private void executePaymentRetry(RetryTask task) {
        unitOfWork.runInTransaction(() -> {
            task.markCompleted();
            retryQueueRepositoryPort.save(task);
            attemptRepositoryPort.save(IntegrationAttempt.ok(
                    task.getTenantId(),
                    task.getProvider(),
                    task.getOperation(),
                    task.getReferenceId(),
                    task.getAttemptsCount() + 1
            ));
        });
    }
}
