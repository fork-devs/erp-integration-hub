package com.erp.integrationhub.application.service;

import com.erp.integrationhub.application.dto.command.ProcessPaymentCommand;
import com.erp.integrationhub.application.dto.result.PaymentResult;
import com.erp.integrationhub.domain.model.IdempotencyKeyState;
import com.erp.integrationhub.domain.model.IdempotencyRecord;
import com.erp.integrationhub.domain.model.IdempotencyScope;
import com.erp.integrationhub.domain.model.PaymentOutcome;
import com.erp.integrationhub.domain.port.IdempotencyRepositoryPort;
import com.erp.integrationhub.domain.port.IntegrationAttemptRepositoryPort;
import com.erp.integrationhub.domain.port.PaymentGatewayPort;
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
    private PaymentGatewayPort paymentGatewayPort;

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
                paymentGatewayPort,
                idempotencyRepositoryPort,
                attemptRepositoryPort,
                DirectUnitOfWork.INSTANCE
        );
    }

    @Test
    @DisplayName("Should successfully charge card via Stripe and record idempotency")
    void processPayment_WhenNewRequest_ShouldCallStripeAndSaveIdempotency() {
        // Given
        ProcessPaymentCommand command = new ProcessPaymentCommand(
                tenantId,
                paymentId,
                new BigDecimal("250.00"),
                "PEN",
                "tok_visa",
                "Brake repair and oil change",
                "client@workshop.pe",
                "idem-pay-01"
        );

        when(idempotencyRepositoryPort.findByTenantAndScopeAndKey(tenantId, IdempotencyScope.PAYMENT, "idem-pay-01"))
                .thenReturn(Optional.empty());

        when(idempotencyRepositoryPort.claim(eq(tenantId), eq(IdempotencyScope.PAYMENT), eq("idem-pay-01"), anyString()))
                .thenReturn(IdempotencyRecord.claiming(tenantId, IdempotencyScope.PAYMENT, "idem-pay-01", "huella"));

        PaymentOutcome outcomeStripe = PaymentOutcome.success("ch_3M4vX...mock", "AUTH-994821");
        when(paymentGatewayPort.processCharge(any())).thenReturn(outcomeStripe);

        // When
        PaymentResult result = paymentIntegrationService.processPayment(command);

        // Then
        assertNotNull(result);
        assertTrue(result.successful());
        assertEquals("SUCCEEDED", result.status());
        assertEquals("ch_3M4vX...mock", result.chargeId());
        assertEquals("AUTH-994821", result.authorizationCode());
        assertFalse(result.cachedResponse());

        verify(attemptRepositoryPort).save(any());
        verify(idempotencyRepositoryPort).claim(eq(tenantId), eq(IdempotencyScope.PAYMENT), eq("idem-pay-01"), anyString());
        verify(idempotencyRepositoryPort).updateByKey(eq(tenantId), eq(IdempotencyScope.PAYMENT), eq("idem-pay-01"), any());
        verify(idempotencyRepositoryPort, never()).save(any());
    }

    @Test
    @DisplayName("Should return cached response when idempotency key is detected")
    void processPayment_WhenIdempotencyExists_ShouldReturnCached() {
        // Given
        ProcessPaymentCommand command = new ProcessPaymentCommand(
                tenantId,
                paymentId,
                new BigDecimal("150.00"),
                "PEN",
                "tok_visa",
                "Wheel alignment and balancing",
                "client@workshop.pe",
                "idem-pay-cached"
        );

        String cachedJson = "{\"successful\":true,\"status\":\"PAID\",\"chargeId\":\"ch_cached_123\",\"clientSecret\":null,\"authorizationCode\":\"AUTH-0001\",\"errorMessage\":null}";
        String fingerprint = PayloadFingerprint.of(String.join("|",
                String.valueOf(tenantId),
                String.valueOf(paymentId),
                "150.00",
                "PEN",
                "tok_visa",
                "Wheel alignment and balancing",
                "client@workshop.pe"));

        IdempotencyRecord record = new IdempotencyRecord(UUID.randomUUID(), tenantId, IdempotencyScope.PAYMENT, "idem-pay-cached", fingerprint, "hash", cachedJson,
                IdempotencyKeyState.COMPLETED, Instant.now());

        when(idempotencyRepositoryPort.findByTenantAndScopeAndKey(tenantId, IdempotencyScope.PAYMENT, "idem-pay-cached"))
                .thenReturn(Optional.of(record));

        // When
        PaymentResult result = paymentIntegrationService.processPayment(command);

        // Then
        assertNotNull(result);
        assertTrue(result.successful());
        assertTrue(result.cachedResponse());
        assertEquals("ch_cached_123", result.chargeId());

        verify(paymentGatewayPort, never()).processCharge(any());
    }
}
