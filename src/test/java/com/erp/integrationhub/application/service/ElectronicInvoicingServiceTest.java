package com.erp.integrationhub.application.service;

import com.erp.integrationhub.application.dto.command.EmitInvoiceCommand;
import com.erp.integrationhub.application.dto.command.InvoiceItemCommand;
import com.erp.integrationhub.application.dto.result.InvoiceResult;
import com.erp.integrationhub.domain.model.DeclarationResult;
import com.erp.integrationhub.domain.model.IdempotencyKeyState;
import com.erp.integrationhub.domain.model.IdempotencyRecord;
import com.erp.integrationhub.domain.model.IdempotencyScope;
import com.erp.integrationhub.domain.model.InvoiceToDeclare;
import com.erp.integrationhub.domain.port.IdempotencyRepositoryPort;
import com.erp.integrationhub.domain.port.IntegrationAttemptRepositoryPort;
import com.erp.integrationhub.domain.port.RetryQueueRepositoryPort;
import com.erp.integrationhub.domain.port.TaxAuthorityEmitterPort;
import com.erp.integrationhub.domain.port.VoucherNumberingPort;
import com.erp.integrationhub.domain.util.PayloadFingerprint;
import com.erp.integrationhub.support.DirectUnitOfWork;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ElectronicInvoicingServiceTest {

    @Mock
    private TaxAuthorityEmitterPort taxAuthorityEmitterPort;

    @Mock
    private IdempotencyRepositoryPort idempotencyRepositoryPort;

    @Mock
    private IntegrationAttemptRepositoryPort attemptRepositoryPort;

    @Mock
    private RetryQueueRepositoryPort retryQueueRepositoryPort;

    @Mock
    private VoucherNumberingPort numberingPort;

    private ElectronicInvoicingService electronicInvoicingService;

    private final UUID tenantId = UUID.randomUUID();
    private final UUID invoiceId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        electronicInvoicingService = new ElectronicInvoicingService(
                taxAuthorityEmitterPort,
                idempotencyRepositoryPort,
                attemptRepositoryPort,
                retryQueueRepositoryPort,
                DirectUnitOfWork.INSTANCE,
                numberingPort
        );
    }

    @Test
    @DisplayName("Should successfully emit invoice to SUNAT and save idempotency record")
    void emitInvoice_WhenNewRequest_ShouldCallProviderAndPersistIdempotency() {
        // Given
        InvoiceItemCommand item = new InvoiceItemCommand("NIU", "Preventive Maintenance", BigDecimal.ONE, new BigDecimal("100.00"), new BigDecimal("118.00"), new BigDecimal("18.00"), new BigDecimal("118.00"));
        EmitInvoiceCommand command = new EmitInvoiceCommand(
                tenantId,
                invoiceId,
                "FACTURA",
                "F001",
                123,
                "6",
                "20123456789",
                "TRANSPORT ENTERPRISE SAC",
                "Av. Industrial 456",
                "accounting@company.com",
                "PEN",
                new BigDecimal("100.00"),
                new BigDecimal("18.00"),
                new BigDecimal("118.00"),
                List.of(item),
                LocalDate.now(),
                "idem-key-001"
        );

        when(idempotencyRepositoryPort.findByTenantAndScopeAndKey(tenantId, IdempotencyScope.VOUCHER, "idem-key-001"))
                .thenReturn(Optional.empty());

        DeclarationResult declarationOk = DeclarationResult.accepted(
                "0",
                "La Factura F001-123 ha sido aceptada por SUNAT",
                "https://storage.pe/pdf/F001-123.pdf",
                "https://storage.pe/xml/F001-123.xml",
                "https://storage.pe/cdr/R-F001-123.zip",
                "HASH123456789"
        );
        when(taxAuthorityEmitterPort.emitInvoice(any(InvoiceToDeclare.class))).thenReturn(declarationOk);

        // When
        InvoiceResult result = electronicInvoicingService.emitInvoice(command);

        // Then
        assertNotNull(result);
        assertTrue(result.successful());
        assertEquals("ACEPTADO", result.sunatStatus());
        assertEquals("https://storage.pe/pdf/F001-123.pdf", result.pdfUrl());
        assertFalse(result.cachedResponse());

        verify(attemptRepositoryPort).save(any());
        verify(idempotencyRepositoryPort).save(any(IdempotencyRecord.class));
        verify(retryQueueRepositoryPort, never()).save(any());
    }

    @Test
    @DisplayName("Should return cached response when idempotency record is found without calling provider")
    void emitInvoice_WhenIdempotencyRecordExists_ShouldReturnCachedResult() {
        // Given
        EmitInvoiceCommand command = new EmitInvoiceCommand(
                tenantId,
                invoiceId,
                "FACTURA",
                "F001",
                123,
                "6",
                "20123456789",
                "EMPRESA SAC",
                null,
                null,
                "PEN",
                BigDecimal.TEN,
                new BigDecimal("1.80"),
                new BigDecimal("11.80"),
                List.of(),
                LocalDate.now(),
                "idem-key-001"
        );

        String cachedJson = "{\"successful\":true,\"sunatStatus\":\"ACEPTADA\",\"sunatResponseCode\":\"0\",\"sunatMessage\":\"Previously accepted\",\"pdfUrl\":\"https://storage.pe/pdf/F001-123.pdf\"}";
        String fingerprint = PayloadFingerprint.of(String.join("|",
                String.valueOf(tenantId),
                String.valueOf(invoiceId),
                "FACTURA",
                "F001",
                "123",
                "20123456789",
                "11.80",
                "PEN"));

        IdempotencyRecord existingRecord = new IdempotencyRecord(UUID.randomUUID(), tenantId, IdempotencyScope.VOUCHER, "idem-key-001", fingerprint, "hash", cachedJson,
                IdempotencyKeyState.COMPLETED, Instant.now());

        when(idempotencyRepositoryPort.findByTenantAndScopeAndKey(tenantId, IdempotencyScope.VOUCHER, "idem-key-001"))
                .thenReturn(Optional.of(existingRecord));

        // When
        InvoiceResult result = electronicInvoicingService.emitInvoice(command);

        // Then
        assertNotNull(result);
        assertTrue(result.successful());
        assertTrue(result.cachedResponse());
        assertEquals("ACEPTADA", result.sunatStatus());
        verify(taxAuthorityEmitterPort, never()).emitInvoice(any());
    }

    @Test
    @DisplayName("Should enqueue into retry queue when SUNAT provider fails with exception")
    void emitInvoice_WhenProviderFails_ShouldEnqueueRetryTaskAndReturnPending() {
        // Given
        InvoiceItemCommand item = new InvoiceItemCommand("NIU", "Mechanical Service", BigDecimal.ONE, BigDecimal.TEN, new BigDecimal("11.80"), new BigDecimal("1.80"), new BigDecimal("11.80"));
        EmitInvoiceCommand command = new EmitInvoiceCommand(
                tenantId,
                invoiceId,
                "BOLETA",
                "B001",
                55,
                "1",
                "45678901",
                "Juan Perez",
                null,
                null,
                "PEN",
                BigDecimal.TEN,
                new BigDecimal("1.80"),
                new BigDecimal("11.80"),
                List.of(item),
                LocalDate.now(),
                "idem-fail-01"
        );

        when(idempotencyRepositoryPort.findByTenantAndScopeAndKey(tenantId, IdempotencyScope.VOUCHER, "idem-fail-01"))
                .thenReturn(Optional.empty());
        when(taxAuthorityEmitterPort.emitInvoice(any())).thenThrow(new RuntimeException("Connection timed out to SUNAT PSE"));

        // When
        InvoiceResult result = electronicInvoicingService.emitInvoice(command);

        // Then
        assertNotNull(result);
        assertTrue(result.successful());
        assertEquals("PENDIENTE_SUNAT", result.sunatStatus());

        verify(retryQueueRepositoryPort).save(any());
        verify(attemptRepositoryPort).save(any());
        verify(idempotencyRepositoryPort).save(any());
    }
}
