package com.erp.integrationhub.support;

import com.erp.integrationhub.application.dto.command.ProcessPaymentCommand;
import com.erp.integrationhub.application.service.PaymentIntegrationService;
import com.erp.integrationhub.domain.exception.IdempotencyConflictException;
import com.erp.integrationhub.domain.model.ChargeRequest;
import com.erp.integrationhub.domain.model.IdempotencyKeyState;
import com.erp.integrationhub.domain.model.IdempotencyScope;
import com.erp.integrationhub.domain.model.PaymentOutcome;
import com.erp.integrationhub.domain.port.IdempotencyRepositoryPort;
import com.erp.integrationhub.domain.port.IntegrationAttemptRepositoryPort;
import com.erp.integrationhub.domain.port.PaymentGatewayPort;
import com.erp.integrationhub.domain.port.UnitOfWork;
import com.erp.integrationhub.infrastructure.persistence.rls.TenantContextHolder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests verifying that concurrent charge requests sharing the same idempotency key never double-charge.
 */
class ConcurrentIdempotencyTest extends AbstractPostgresIntegrationTest {

    private static final UUID TENANT_ID = UUID.fromString("ffff1111-2222-3333-4444-555555555555");

    @Autowired
    private PaymentIntegrationService paymentIntegrationService;

    @Autowired
    private IdempotencyRepositoryPort idempotencyRepositoryPort;

    @Autowired
    private UnitOfWork unitOfWork;

    @Autowired
    private PaymentGatewayPort paymentGatewayPort;

    @Autowired
    private IntegrationAttemptRepositoryPort attemptRepositoryPort;

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(TENANT_ID);
    }

    @Test
    @DisplayName("Two concurrent requests with the same key invoke the gateway exactly once")
    void twoConcurrentRequestsWithSameKeyInvokeGatewayOnce() throws Exception {
        AtomicInteger gatewayCalls = new AtomicInteger();
        String idempotencyKey = "concurrent-key-" + UUID.randomUUID();

        PaymentGatewayPort countingGateway = new PaymentGatewayPort() {
            @Override
            public PaymentOutcome processCharge(ChargeRequest request) {
                gatewayCalls.incrementAndGet();
                try {
                    Thread.sleep(200);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return PaymentOutcome.success("ch_concurrent", "AUTH-1");
            }

            @Override
            public PaymentOutcome queryPaymentStatus(String chargeId) {
                return PaymentOutcome.success(chargeId, "AUTH-1");
            }
        };

        PaymentIntegrationService service = new PaymentIntegrationService(
                countingGateway,
                idempotencyRepositoryPort,
                attemptRepositoryPort,
                unitOfWork);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch startLatch = new CountDownLatch(1);
        AtomicInteger conflicts = new AtomicInteger();

        try {
            for (int i = 0; i < 2; i++) {
                executor.submit(() -> {
                    TenantContextHolder.setTenantId(TENANT_ID);
                    try {
                        startLatch.await(5, TimeUnit.SECONDS);
                        service.processPayment(new ProcessPaymentCommand(
                                TENANT_ID,
                                UUID.randomUUID(),
                                new BigDecimal("50.00"),
                                "PEN",
                                "tok_test",
                                "Concurrent test",
                                "client@workshop.pe",
                                idempotencyKey));
                    } catch (IdempotencyConflictException e) {
                        conflicts.incrementAndGet();
                    } catch (Exception ignored) {
                    }
                });
            }
            startLatch.countDown();
            executor.shutdown();
            assertTrue(executor.awaitTermination(30, TimeUnit.SECONDS), "Requests must complete within timeout");
        } finally {
            executor.shutdownNow();
        }

        assertEquals(1, gatewayCalls.get(),
                "Provider should be called exactly ONCE: double charging must be prevented");
        assertTrue(conflicts.get() >= 1,
                "Second concurrent request must receive idempotency conflict exception");
    }

    @Test
    @DisplayName("A released idempotency key can be reclaimed by subsequent retry")
    void releasedKeyCanBeReclaimed() {
        String key = "released-key-" + UUID.randomUUID();

        var claimed = unitOfWork.callInTransaction(() ->
                idempotencyRepositoryPort.claim(TENANT_ID, IdempotencyScope.PAYMENT, key, "fingerprint"));
        assertEquals(IdempotencyKeyState.IN_PROGRESS, claimed.getState());

        unitOfWork.runInTransaction(() -> idempotencyRepositoryPort.updateByKey(
                TENANT_ID, IdempotencyScope.PAYMENT, key, claimed.released()));

        var reclaimed = unitOfWork.callInTransaction(() ->
                idempotencyRepositoryPort.claim(TENANT_ID, IdempotencyScope.PAYMENT, key, "fingerprint"));
        assertEquals(IdempotencyKeyState.IN_PROGRESS, reclaimed.getState(),
                "A released key must allow re-claiming for retries");
    }
}
