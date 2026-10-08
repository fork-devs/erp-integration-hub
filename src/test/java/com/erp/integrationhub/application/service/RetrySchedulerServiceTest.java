package com.erp.integrationhub.application.service;

import com.erp.integrationhub.domain.model.ProviderType;
import com.erp.integrationhub.domain.model.RetryTask;
import com.erp.integrationhub.domain.port.EmisorTributarioPort;
import com.erp.integrationhub.domain.port.IntegrationAttemptRepositoryPort;
import com.erp.integrationhub.domain.port.PasarelaDePagoPort;
import com.erp.integrationhub.domain.port.RetryQueueRepositoryPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
    private EmisorTributarioPort emisorTributarioPort;

    @Mock
    private PasarelaDePagoPort pasarelaDePagoPort;

    private RetrySchedulerService retrySchedulerService;

    @BeforeEach
    void setUp() {
        retrySchedulerService = new RetrySchedulerService(
                retryQueueRepositoryPort,
                attemptRepositoryPort,
                emisorTributarioPort,
                pasarelaDePagoPort
        );
    }

    @Test
    @DisplayName("Should process pending retry tasks and mark completed")
    void processPendingRetries_WhenTasksExist_ShouldMarkCompleted() {
        RetryTask task1 = RetryTask.create(UUID.randomUUID(), ProviderType.APISUNAT, "EMIT_VOUCHER", UUID.randomUUID(), "{}", 5);
        RetryTask task2 = RetryTask.create(UUID.randomUUID(), ProviderType.STRIPE, "CHARGE", UUID.randomUUID(), "{}", 5);

        when(retryQueueRepositoryPort.findPendingTasks(any(Instant.class), eq(10)))
                .thenReturn(List.of(task1, task2));

        int processed = retrySchedulerService.processPendingRetries(10);

        assertEquals(2, processed);
        assertEquals("COMPLETED", task1.getStatus());
        assertEquals("COMPLETED", task2.getStatus());

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
