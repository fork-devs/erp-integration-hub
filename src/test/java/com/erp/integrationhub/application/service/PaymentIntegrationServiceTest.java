package com.erp.integrationhub.application.service;

import com.erp.integrationhub.application.dto.command.ProcesarPagoCommand;
import com.erp.integrationhub.application.dto.result.PagoResult;
import com.erp.integrationhub.domain.model.IdempotencyKeyState;
import com.erp.integrationhub.domain.model.IdempotencyRecord;
import com.erp.integrationhub.domain.model.IdempotencyScope;
import com.erp.integrationhub.domain.model.ResultadoPago;
import com.erp.integrationhub.domain.port.IdempotencyRepositoryPort;
import com.erp.integrationhub.domain.port.IntegrationAttemptRepositoryPort;
import com.erp.integrationhub.domain.port.PasarelaDePagoPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import com.erp.integrationhub.support.DirectUnitOfWork;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentIntegrationServiceTest {

    @Mock
    private PasarelaDePagoPort pasarelaDePagoPort;

    @Mock
    private IdempotencyRepositoryPort idempotencyRepositoryPort;

    @Mock
    private IntegrationAttemptRepositoryPort attemptRepositoryPort;

    private PaymentIntegrationService paymentIntegrationService;

    private final UUID tenantId = UUID.randomUUID();
    private final UUID paymentId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        paymentIntegrationService = new PaymentIntegrationService(
                pasarelaDePagoPort,
                idempotencyRepositoryPort,
                attemptRepositoryPort,
                DirectUnitOfWork.INSTANCE
        );
    }

    @Test
    @DisplayName("Should successfully charge card via Stripe and record idempotency")
    void procesarPago_WhenNewRequest_ShouldCallStripeAndSaveIdempotency() {
        // Given
        ProcesarPagoCommand command = new ProcesarPagoCommand(
                tenantId,
                paymentId,
                new BigDecimal("250.00"),
                "PEN",
                "tok_visa",
                "Reparacion de Frenos y Cambio de Aceite",
                "cliente@taller.pe",
                "idem-pay-01"
        );

        when(idempotencyRepositoryPort.findByTenantAndScopeAndKey(tenantId, IdempotencyScope.PAYMENT, "idem-pay-01"))
                .thenReturn(Optional.empty());

        // La clave se RECLAMA antes de contactar al proveedor, y la
        // reclamacion devuelve el registro que se actualizara despues.
        when(idempotencyRepositoryPort.claim(eq(tenantId), eq(IdempotencyScope.PAYMENT), eq("idem-pay-01"), anyString()))
                .thenReturn(IdempotencyRecord.claiming(tenantId, IdempotencyScope.PAYMENT, "idem-pay-01", "huella"));

        ResultadoPago resultadoStripe = ResultadoPago.exitoso("ch_3M4vX...mock", "AUTH-994821");
        when(pasarelaDePagoPort.procesarCobro(any())).thenReturn(resultadoStripe);

        // When
        PagoResult result = paymentIntegrationService.procesarPago(command);

        // Then
        assertNotNull(result);
        assertTrue(result.exitoso());
        assertEquals("SUCCEEDED", result.estado());
        assertEquals("ch_3M4vX...mock", result.chargeId());
        assertEquals("AUTH-994821", result.codigoAutorizacion());
        assertFalse(result.cachedResponse());

        verify(attemptRepositoryPort).save(any());
        // El resultado se ESCRIBE sobre la fila reclamada, no en una nueva.
        verify(idempotencyRepositoryPort).claim(eq(tenantId), eq(IdempotencyScope.PAYMENT), eq("idem-pay-01"), anyString());
        verify(idempotencyRepositoryPort).updateByKey(eq(tenantId), eq(IdempotencyScope.PAYMENT), eq("idem-pay-01"), any());
        verify(idempotencyRepositoryPort, never()).save(any());
    }

    @Test
    @DisplayName("Should return cached response when idempotency key is detected")
    void procesarPago_WhenIdempotencyExists_ShouldReturnCached() {
        // Given
        ProcesarPagoCommand command = new ProcesarPagoCommand(
                tenantId,
                paymentId,
                new BigDecimal("150.00"),
                "PEN",
                "tok_visa",
                "Alineamiento y balanceo",
                "cliente@taller.pe",
                "idem-pay-cached"
        );

        String cachedJson = "{\"exitoso\":true,\"estado\":\"PAID\",\"chargeId\":\"ch_cached_123\",\"clientSecret\":null,\"codigoAutorizacion\":\"AUTH-0001\",\"mensajeError\":null}";
        // El registro almacenado lleva la huella de la MISMA peticion: con
        // otra huella, la clave se rechaza por conflicto, porque no es la
        // misma peticion.
        String huella = com.erp.integrationhub.domain.util.PayloadFingerprint.of(String.join("|",
                String.valueOf(tenantId),
                String.valueOf(paymentId),
                "150.00",
                "PEN",
                "tok_visa",
                "Alineamiento y balanceo",
                "cliente@taller.pe"));

        IdempotencyRecord record = new IdempotencyRecord(UUID.randomUUID(), tenantId, IdempotencyScope.PAYMENT, "idem-pay-cached", huella, "hash", cachedJson,
                IdempotencyKeyState.COMPLETED, java.time.Instant.now());

        when(idempotencyRepositoryPort.findByTenantAndScopeAndKey(tenantId, IdempotencyScope.PAYMENT, "idem-pay-cached"))
                .thenReturn(Optional.of(record));

        // When
        PagoResult result = paymentIntegrationService.procesarPago(command);

        // Then
        assertNotNull(result);
        assertTrue(result.exitoso());
        assertTrue(result.cachedResponse());
        assertEquals("ch_cached_123", result.chargeId());

        verify(pasarelaDePagoPort, never()).procesarCobro(any());
    }
}
