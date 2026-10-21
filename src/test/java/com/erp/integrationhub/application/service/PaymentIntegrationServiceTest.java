package com.erp.integrationhub.application.service;

import com.erp.integrationhub.application.dto.command.ProcesarPagoCommand;
import com.erp.integrationhub.application.dto.result.PagoResult;
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
        verify(idempotencyRepositoryPort).save(any());
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
        IdempotencyRecord record = new IdempotencyRecord(UUID.randomUUID(), tenantId, IdempotencyScope.PAYMENT, "idem-pay-cached", "hash", cachedJson, null);

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
