package com.erp.integrationhub.application.service;

import com.erp.integrationhub.application.dto.command.EmitInvoiceCommand;
import com.erp.integrationhub.application.dto.command.InvoiceItemCommand;
import com.erp.integrationhub.domain.model.DeclarationResult;
import com.erp.integrationhub.domain.model.IdempotencyRecord;
import com.erp.integrationhub.domain.model.IdempotencyScope;
import com.erp.integrationhub.domain.model.ProviderType;
import com.erp.integrationhub.domain.model.RetryTask;
import com.erp.integrationhub.domain.port.IdempotencyRepositoryPort;
import com.erp.integrationhub.domain.port.IntegrationAttemptRepositoryPort;
import com.erp.integrationhub.domain.port.PaymentGatewayPort;
import com.erp.integrationhub.domain.port.RetryQueueRepositoryPort;
import com.erp.integrationhub.domain.port.TaxAuthorityEmitterPort;
import com.erp.integrationhub.support.DirectUnitOfWork;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RetrySchedulerServiceTest {

    @Mock
    private RetryQueueRepositoryPort retryQueueRepositoryPort;

    @Mock
    private IntegrationAttemptRepositoryPort attemptRepositoryPort;

    @Mock
    private IdempotencyRepositoryPort idempotencyRepositoryPort;

    @Mock
    private TaxAuthorityEmitterPort taxAuthorityEmitterPort;

    @Mock
    private PaymentGatewayPort paymentGatewayPort;

    private RetrySchedulerService retrySchedulerService;
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @BeforeEach
    void setUp() {
        lenient().when(idempotencyRepositoryPort.findByTenantAndScopeAndKey(any(), any(), any()))
                .thenReturn(Optional.empty());
        lenient().when(idempotencyRepositoryPort.claim(any(), any(), any(), any()))
                .thenReturn(IdempotencyRecord.claiming(
                        UUID.randomUUID(),
                        IdempotencyScope.VOUCHER,
                        "clave", "huella"));
        retrySchedulerService = new RetrySchedulerService(
                retryQueueRepositoryPort,
                attemptRepositoryPort,
                idempotencyRepositoryPort,
                taxAuthorityEmitterPort,
                paymentGatewayPort,
                DirectUnitOfWork.INSTANCE
        );
    }

    @Test
    @DisplayName("Should process pending retry tasks, execute provider, update idempotency, and mark completed")
    void processPendingRetries_WhenTasksExist_ShouldExecuteProviderAndMarkCompleted() throws Exception {
        UUID tenantId = UUID.randomUUID();
        UUID invoiceId = UUID.randomUUID();

        EmitInvoiceCommand cmd = new EmitInvoiceCommand(
                tenantId,
                invoiceId,
                "FACTURA",
                "F001",
                123,
                "6",
                "20123456789",
                "EMPRESA TEST SAC",
                "LIMA",
                "test@test.pe",
                "PEN",
                new BigDecimal("100.00"),
                new BigDecimal("18.00"),
                new BigDecimal("118.00"),
                List.of(new InvoiceItemCommand("NIU", "Servicio", BigDecimal.ONE, new BigDecimal("100.00"), new BigDecimal("118.00"), new BigDecimal("18.00"), new BigDecimal("118.00"))),
                LocalDate.now(),
                "idem-key-1"
        );

        String jsonPayload = objectMapper.writeValueAsString(cmd);
        RetryTask task1 = RetryTask.create(tenantId, ProviderType.APISUNAT, "EMIT_VOUCHER", invoiceId, jsonPayload, 5);
        RetryTask task2 = RetryTask.create(tenantId, ProviderType.STRIPE, "PROCESS_PAYMENT", UUID.randomUUID(), "{}", 5);

        when(retryQueueRepositoryPort.findPendingTasks(any(Instant.class), eq(10)))
                .thenReturn(List.of(task1, task2));
        when(taxAuthorityEmitterPort.emitInvoice(any()))
                .thenReturn(DeclarationResult.accepted("0", "Aceptado por SUNAT", "pdf", "xml", "cdr", "hash"));

        int processed = retrySchedulerService.processPendingRetries(10);

        assertEquals(2, processed);
        assertEquals("COMPLETED", task1.getStatus());
        assertEquals("COMPLETED", task2.getStatus());

        verify(taxAuthorityEmitterPort).emitInvoice(any());
        verify(idempotencyRepositoryPort).updateByKey(any(), any(), any(), any());
        verify(idempotencyRepositoryPort, never()).save(any());
        verify(retryQueueRepositoryPort, times(2)).save(any());
        verify(attemptRepositoryPort, times(2)).save(any());
    }

    @Test
    @DisplayName("Should return 0 when there are no pending tasks")
    void processPendingRetries_WhenEmpty_ShouldReturnZero() {
        when(retryQueueRepositoryPort.findPendingTasks(any(Instant.class), eq(10)))
                .thenReturn(List.of());

        int processed = retrySchedulerService.processPendingRetries(10);

        assertEquals(0, processed);
    }
}
