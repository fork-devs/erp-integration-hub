package com.erp.integrationhub.support;

import com.erp.integrationhub.application.dto.command.ProcessPaymentCommand;
import com.erp.integrationhub.application.service.PaymentIntegrationService;
import com.erp.integrationhub.domain.model.IdempotencyScope;
import com.erp.integrationhub.domain.port.IdempotencyRepositoryPort;
import com.erp.integrationhub.domain.port.IntegrationAttemptRepositoryPort;
import com.erp.integrationhub.domain.port.PaymentGatewayPort;
import com.erp.integrationhub.domain.port.UnitOfWork;
import com.erp.integrationhub.infrastructure.persistence.entity.IdempotencyKeyJpaEntity;
import com.erp.integrationhub.infrastructure.persistence.repository.SpringDataIdempotencyRepository;
import com.erp.integrationhub.infrastructure.persistence.rls.TenantContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

/**
 * Tests ensuring atomic persistence of payment attempts and idempotency records.
 */
class PaymentAtomicityTest extends AbstractPostgresIntegrationTest {

    private static final UUID TENANT_ID = UUID.fromString("bbbb2222-4444-5555-6666-777788889999");

    private UUID paymentId;
    private String idempotencyKey;

    @Autowired
    private PaymentIntegrationService paymentIntegrationService;

    @Autowired
    private IdempotencyRepositoryPort idempotencyRepositoryPort;

    @Autowired
    private IntegrationAttemptRepositoryPort attemptRepositoryPort;

    @Autowired
    private UnitOfWork unitOfWork;

    @Autowired
    private PaymentGatewayPort paymentGatewayPort;

    @MockitoSpyBean
    private SpringDataIdempotencyRepository springDataIdempotencyRepository;

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(TENANT_ID);
        paymentId = UUID.randomUUID();
        idempotencyKey = "key-" + UUID.randomUUID();
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    @DisplayName("Failure while writing idempotency rolls back attempt record atomically")
    void failureWritingIdempotencyDoesNotLeaveOrphanAttempt() {
        doThrow(new RuntimeException("cannot write idempotency"))
                .when(springDataIdempotencyRepository).save(any(IdempotencyKeyJpaEntity.class));

        assertThrows(RuntimeException.class, this::executeCharge);

        assertEquals(0, countRegisteredAttempts(),
                "No payment attempt should remain registered without its idempotency key");
        assertTrue(findIdempotencyRecord().isEmpty(),
                "No idempotency record should remain without its payment attempt");
    }

    @Test
    @DisplayName("Without failure, both attempt and idempotency records commit together")
    void withoutFailureBothWritesCommitTogether() {
        executeCharge();

        assertEquals(1, countRegisteredAttempts(), "Payment attempt must be recorded");
        assertEquals(1, findIdempotencyRecord().size(), "Idempotency record must be recorded");
    }

    private void executeCharge() {
        paymentIntegrationService.processPayment(new ProcessPaymentCommand(
                TENANT_ID,
                paymentId,
                new BigDecimal("50.00"),
                "PEN",
                "tok_test_123",
                "Test charge",
                "client@workshop.pe",
                idempotencyKey));
    }

    private long countRegisteredAttempts() {
        return attemptRepositoryPort.findByTenantIdAndReferenceId(TENANT_ID, paymentId).size();
    }

    private List<?> findIdempotencyRecord() {
        return idempotencyRepositoryPort
                .findByTenantAndScopeAndKey(TENANT_ID, IdempotencyScope.PAYMENT, idempotencyKey)
                .map(List::of)
                .orElse(List.of());
    }
}
