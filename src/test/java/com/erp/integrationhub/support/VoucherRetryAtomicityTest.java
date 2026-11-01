package com.erp.integrationhub.support;

import com.erp.integrationhub.application.service.RetrySchedulerService;
import com.erp.integrationhub.domain.model.DeclarationResult;
import com.erp.integrationhub.domain.model.IdempotencyScope;
import com.erp.integrationhub.domain.model.ProviderType;
import com.erp.integrationhub.domain.model.RetryTask;
import com.erp.integrationhub.domain.port.IdempotencyRepositoryPort;
import com.erp.integrationhub.domain.port.IntegrationAttemptRepositoryPort;
import com.erp.integrationhub.domain.port.PaymentGatewayPort;
import com.erp.integrationhub.domain.port.RetryQueueRepositoryPort;
import com.erp.integrationhub.domain.port.TaxAuthorityEmitterPort;
import com.erp.integrationhub.domain.port.UnitOfWork;
import com.erp.integrationhub.infrastructure.persistence.entity.IdempotencyKeyJpaEntity;
import com.erp.integrationhub.infrastructure.persistence.repository.SpringDataIdempotencyRepository;
import com.erp.integrationhub.infrastructure.persistence.rls.TenantContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

/**
 * Tests ensuring that a retry already accepted by downstream provider is never re-transmitted.
 */
class VoucherRetryAtomicityTest extends AbstractPostgresIntegrationTest {

    private static final UUID TENANT_ID = UUID.fromString("dddd4444-5555-6666-7777-888899990000");
    private static final UUID INVOICE_ID = UUID.fromString("eeee5555-6666-7777-8888-999900001111");

    @Autowired
    private RetrySchedulerService retrySchedulerService;

    @Autowired
    private RetryQueueRepositoryPort retryQueueRepositoryPort;

    @Autowired
    private IdempotencyRepositoryPort idempotencyRepositoryPort;

    @Autowired
    private IntegrationAttemptRepositoryPort attemptRepositoryPort;

    @Autowired
    private UnitOfWork unitOfWork;

    @Autowired
    private TaxAuthorityEmitterPort taxAuthorityEmitterPort;

    @Autowired
    private PaymentGatewayPort paymentGatewayPort;

    @MockitoSpyBean
    private SpringDataIdempotencyRepository springDataIdempotencyRepository;

    @MockitoBean
    private TaxAuthorityEmitterPort taxAuthorityEmitterPortStub;

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(TENANT_ID);
        when(taxAuthorityEmitterPortStub.emitInvoice(any()))
                .thenReturn(DeclarationResult.accepted(
                        "0", "La factura numero F001-1, ha sido aceptada", null, null, null, null));
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    @DisplayName("Failure while persisting idempotency does not reschedule already-emitted invoice")
    void failureWritingIdempotencyDoesNotRescheduleInvoice() {
        RetryTask task = createPendingTask();

        doThrow(new RuntimeException("cannot write idempotency"))
                .when(springDataIdempotencyRepository).save(any(IdempotencyKeyJpaEntity.class));

        retrySchedulerService.processPendingRetries(10);

        RetryTask after = retryQueueRepositoryPort.findById(task.getId()).orElseThrow();
        assertNotEquals("PENDING", after.getStatus(),
                "Task must not return to pending queue when invoice has already been emitted");
        assertEquals("COMPLETED", after.getStatus(),
                "Invoice was accepted by provider: task must complete and not re-emit duplicate invoice");
        assertEquals(0, idempotencyRepositoryPort
                .findByTenantAndScopeAndKey(TENANT_ID,
                        IdempotencyScope.VOUCHER,
                        INVOICE_ID.toString())
                .map(List::of)
                .orElse(List.of())
                .size(),
                "Idempotency record failed persistence as simulated by spy");
    }

    @Test
    @DisplayName("Without failure, task completes successfully")
    void withoutFailureTaskCompletes() {
        RetryTask task = createPendingTask();

        retrySchedulerService.processPendingRetries(10);

        RetryTask after = retryQueueRepositoryPort.findById(task.getId()).orElseThrow();
        assertEquals("COMPLETED", after.getStatus(),
                "Task should be marked completed on successful provider response");
    }

    private RetryTask createPendingTask() {
        String payload = """
                {
                  "tenantId": "%s",
                  "comprobanteId": "%s",
                  "tipo": "FACTURA",
                  "serie": "F001",
                  "numero": 1,
                  "clienteTipoDoc": "DNI",
                  "clienteNumDoc": "12345678",
                  "clienteDenominacion": "Cliente de prueba",
                  "clienteDireccion": "Av. Siempre Viva 742",
                  "clienteEmail": "cliente@taller.pe",
                  "moneda": "PEN",
                  "totalGravada": 100.00,
                  "totalIgv": 18.00,
                  "total": 118.00,
                  "items": [
                    {
                      "unidadMedida": "NIU",
                      "descripcion": "Servicio de prueba",
                      "cantidad": 1,
                      "valorUnitario": 100.00,
                      "precioUnitario": 118.00,
                      "igv": 18.00,
                      "total": 118.00
                    }
                  ],
                  "fechaEmision": "2026-01-15",
                  "idempotencyKey": "%s"
                }
                """.formatted(TENANT_ID, INVOICE_ID, INVOICE_ID);

        Instant now = Instant.now();
        RetryTask task = new RetryTask(
                UUID.randomUUID(),
                TENANT_ID,
                ProviderType.APISUNAT,
                "EMIT_VOUCHER",
                INVOICE_ID,
                payload,
                0,
                5,
                now.minusSeconds(60),
                "PENDING",
                null,
                now,
                now);

        unitOfWork.runInTransaction(() -> retryQueueRepositoryPort.save(task));
        return task;
    }
}
