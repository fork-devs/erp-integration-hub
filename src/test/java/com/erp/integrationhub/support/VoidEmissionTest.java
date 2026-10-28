package com.erp.integrationhub.support;

import com.erp.integrationhub.application.dto.result.InvoiceResult;
import com.erp.integrationhub.application.service.ElectronicInvoicingService;
import com.erp.integrationhub.domain.exception.ProviderUnavailableException;
import com.erp.integrationhub.domain.model.DeclarationResult;
import com.erp.integrationhub.domain.port.IdempotencyRepositoryPort;
import com.erp.integrationhub.domain.port.IntegrationAttemptRepositoryPort;
import com.erp.integrationhub.domain.port.RetryQueueRepositoryPort;
import com.erp.integrationhub.domain.port.TaxAuthorityEmitterPort;
import com.erp.integrationhub.domain.port.UnitOfWork;
import com.erp.integrationhub.domain.port.VoucherNumberingPort;
import com.erp.integrationhub.infrastructure.persistence.rls.TenantContextHolder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests verifying electronic invoice voiding logic, communication tickets, and retry rescheduling on transient errors.
 */
class VoidEmissionTest extends AbstractPostgresIntegrationTest {

    private static final UUID TENANT_ID = UUID.fromString("cc11cc22-cc33-cc44-cc55-cc66cc77cc88");

    private TaxAuthorityEmitterPort taxAuthorityEmitterPort;
    private IntegrationAttemptRepositoryPort attemptRepositoryPort;
    private IdempotencyRepositoryPort idempotencyRepositoryPort;
    private RetryQueueRepositoryPort retryQueueRepositoryPort;

    @Autowired
    private UnitOfWork unitOfWork;

    private ElectronicInvoicingService electronicInvoicingService;

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(TENANT_ID);
        taxAuthorityEmitterPort = mock(TaxAuthorityEmitterPort.class);
        attemptRepositoryPort = mock(IntegrationAttemptRepositoryPort.class);
        idempotencyRepositoryPort = mock(IdempotencyRepositoryPort.class);
        retryQueueRepositoryPort = mock(RetryQueueRepositoryPort.class);
        electronicInvoicingService = new ElectronicInvoicingService(
                taxAuthorityEmitterPort,
                idempotencyRepositoryPort,
                attemptRepositoryPort,
                retryQueueRepositoryPort,
                unitOfWork,
                mock(VoucherNumberingPort.class));
    }

    @Test
    @DisplayName("A transient voiding failure is rescheduled to retry outbox rather than returned as final rejection")
    void transientVoidingFailureIsRescheduled() {
        when(taxAuthorityEmitterPort.voidInvoice(anyString(), anyString(), anyInt(), anyString()))
                .thenThrow(new ProviderUnavailableException("Provider temporarily unavailable"));

        InvoiceResult result = electronicInvoicingService.voidInvoice(
                TENANT_ID, UUID.randomUUID(), "FACTURA", "F001", 123, "Emission error");

        assertTrue(result.successful(),
                "A rescheduled voiding is not a final rejection; client can safely retry");
        assertEquals("PENDIENTE_SUNAT", result.sunatStatus());

        verify(retryQueueRepositoryPort).save(any());
        verify(attemptRepositoryPort).save(any());
    }

    @Test
    @DisplayName("A definitive voiding rejection is returned to the client and not rescheduled")
    void definitiveVoidingRejectionIsNotRescheduled() {
        when(taxAuthorityEmitterPort.voidInvoice(anyString(), anyString(), anyInt(), anyString()))
                .thenReturn(DeclarationResult.rejected("2335", "Voucher cannot be voided"));

        InvoiceResult result = electronicInvoicingService.voidInvoice(
                TENANT_ID, UUID.randomUUID(), "FACTURA", "F001", 123, "Emission error");

        assertFalse(result.successful(), "Definitive rejection is returned to client");
        assertEquals("RECHAZADO", result.sunatStatus());
        assertEquals("2335", result.sunatResponseCode(),
                "Provider response code is preserved in result");

        verify(retryQueueRepositoryPort, never()).save(any());
    }
}
