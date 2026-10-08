package com.erp.integrationhub.application.service;

import com.erp.integrationhub.application.dto.command.EmitirComprobanteCommand;
import com.erp.integrationhub.application.dto.command.ItemComprobanteCommand;
import com.erp.integrationhub.application.dto.result.ComprobanteResult;
import com.erp.integrationhub.domain.model.ComprobanteParaDeclarar;
import com.erp.integrationhub.domain.model.IdempotencyRecord;
import com.erp.integrationhub.domain.model.IdempotencyScope;
import com.erp.integrationhub.domain.model.ResultadoDeclaracion;
import com.erp.integrationhub.domain.port.EmisorTributarioPort;
import com.erp.integrationhub.domain.port.IdempotencyRepositoryPort;
import com.erp.integrationhub.domain.port.IntegrationAttemptRepositoryPort;
import com.erp.integrationhub.domain.port.RetryQueueRepositoryPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ElectronicInvoicingServiceTest {

    @Mock
    private EmisorTributarioPort emisorTributarioPort;

    @Mock
    private IdempotencyRepositoryPort idempotencyRepositoryPort;

    @Mock
    private IntegrationAttemptRepositoryPort attemptRepositoryPort;

    @Mock
    private RetryQueueRepositoryPort retryQueueRepositoryPort;

    private ElectronicInvoicingService electronicInvoicingService;

    private final UUID tenantId = UUID.randomUUID();
    private final UUID comprobanteId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        electronicInvoicingService = new ElectronicInvoicingService(
                emisorTributarioPort,
                idempotencyRepositoryPort,
                attemptRepositoryPort,
                retryQueueRepositoryPort
        );
    }

    @Test
    @DisplayName("Should successfully emit invoice to SUNAT and save idempotency record")
    void emitirComprobante_WhenNewRequest_ShouldCallProviderAndPersistIdempotency() {
        // Given
        ItemComprobanteCommand item = new ItemComprobanteCommand("NIU", "Mantenimiento Preventivo", BigDecimal.ONE, new BigDecimal("100.00"), new BigDecimal("118.00"), new BigDecimal("18.00"), new BigDecimal("118.00"));
        EmitirComprobanteCommand command = new EmitirComprobanteCommand(
                tenantId,
                comprobanteId,
                "FACTURA",
                "F001",
                123,
                "6",
                "20123456789",
                "EMPRESA DE TRANSPORTE SAC",
                "Av. Industrial 456",
                "contabilidad@empresa.com",
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

        ResultadoDeclaracion declaracionOk = ResultadoDeclaracion.aceptado(
                "0",
                "La Factura F001-123 ha sido aceptada por SUNAT",
                "https://storage.pe/pdf/F001-123.pdf",
                "https://storage.pe/xml/F001-123.xml",
                "https://storage.pe/cdr/R-F001-123.zip",
                "HASH123456789"
        );
        when(emisorTributarioPort.emitirComprobante(any(ComprobanteParaDeclarar.class))).thenReturn(declaracionOk);

        // When
        ComprobanteResult result = electronicInvoicingService.emitirComprobante(command);

        // Then
        assertNotNull(result);
        assertTrue(result.exitoso());
        assertEquals("ACEPTADO", result.estadoSunat());
        assertEquals("https://storage.pe/pdf/F001-123.pdf", result.pdfUrl());
        assertFalse(result.cachedResponse());

        verify(attemptRepositoryPort).save(any());
        verify(idempotencyRepositoryPort).save(any(IdempotencyRecord.class));
        verify(retryQueueRepositoryPort, never()).save(any());
    }

    @Test
    @DisplayName("Should return cached response when idempotency record is found without calling provider")
    void emitirComprobante_WhenIdempotencyRecordExists_ShouldReturnCachedResult() {
        // Given
        EmitirComprobanteCommand command = new EmitirComprobanteCommand(
                tenantId,
                comprobanteId,
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

        String cachedJson = "{\"exitoso\":true,\"estadoSunat\":\"ACEPTADA\",\"codigo\":\"0\",\"mensaje\":\"Aceptada previamente\",\"pdfUrl\":\"https://storage.pe/pdf/F001-123.pdf\"}";
        IdempotencyRecord existingRecord = new IdempotencyRecord(UUID.randomUUID(), tenantId, IdempotencyScope.VOUCHER, "idem-key-001", "hash", cachedJson, null);

        when(idempotencyRepositoryPort.findByTenantAndScopeAndKey(tenantId, IdempotencyScope.VOUCHER, "idem-key-001"))
                .thenReturn(Optional.of(existingRecord));

        // When
        ComprobanteResult result = electronicInvoicingService.emitirComprobante(command);

        // Then
        assertNotNull(result);
        assertTrue(result.exitoso());
        assertTrue(result.cachedResponse());
        assertEquals("ACEPTADA", result.estadoSunat());
        verify(emisorTributarioPort, never()).emitirComprobante(any());
    }

    @Test
    @DisplayName("Should enqueue into retry queue when SUNAT provider fails with exception")
    void emitirComprobante_WhenProviderFails_ShouldEnqueueRetryTaskAndReturnPending() {
        // Given
        ItemComprobanteCommand item = new ItemComprobanteCommand("NIU", "Servicio Mecanico", BigDecimal.ONE, BigDecimal.TEN, new BigDecimal("11.80"), new BigDecimal("1.80"), new BigDecimal("11.80"));
        EmitirComprobanteCommand command = new EmitirComprobanteCommand(
                tenantId,
                comprobanteId,
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
        when(emisorTributarioPort.emitirComprobante(any())).thenThrow(new RuntimeException("Connection timed out to SUNAT PSE"));

        // When
        ComprobanteResult result = electronicInvoicingService.emitirComprobante(command);

        // Then
        assertNotNull(result);
        assertTrue(result.exitoso());
        assertEquals("PENDIENTE_SUNAT", result.estadoSunat());

        verify(retryQueueRepositoryPort).save(any());
        verify(attemptRepositoryPort).save(any());
        verify(idempotencyRepositoryPort).save(any());
    }
}
