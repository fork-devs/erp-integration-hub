package com.erp.integrationhub.application.service;

import com.erp.integrationhub.application.usecase.ProcessRetryQueueUseCase;
import com.erp.integrationhub.domain.model.IntegrationAttempt;
import com.erp.integrationhub.domain.model.RetryTask;
import com.erp.integrationhub.domain.port.EmisorTributarioPort;
import com.erp.integrationhub.domain.port.IntegrationAttemptRepositoryPort;
import com.erp.integrationhub.domain.port.PasarelaDePagoPort;
import com.erp.integrationhub.domain.port.RetryQueueRepositoryPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.List;

public class RetrySchedulerService implements ProcessRetryQueueUseCase {

    private static final Logger log = LoggerFactory.getLogger(RetrySchedulerService.class);

    private final RetryQueueRepositoryPort retryQueueRepositoryPort;
    private final IntegrationAttemptRepositoryPort attemptRepositoryPort;
    private final EmisorTributarioPort emisorTributarioPort;
    private final PasarelaDePagoPort pasarelaDePagoPort;

    public RetrySchedulerService(
            RetryQueueRepositoryPort retryQueueRepositoryPort,
            IntegrationAttemptRepositoryPort attemptRepositoryPort,
            EmisorTributarioPort emisorTributarioPort,
            PasarelaDePagoPort pasarelaDePagoPort) {
        this.retryQueueRepositoryPort = retryQueueRepositoryPort;
        this.attemptRepositoryPort = attemptRepositoryPort;
        this.emisorTributarioPort = emisorTributarioPort;
        this.pasarelaDePagoPort = pasarelaDePagoPort;
    }

    @Override
    public int processPendingRetries(int batchSize) {
        List<RetryTask> pendingTasks = retryQueueRepositoryPort.findPendingTasks(Instant.now(), batchSize);
        int processed = 0;

        for (RetryTask task : pendingTasks) {
            try {
                log.info("Reintentando tarea {} para proveedor {} (Intento {}/{})",
                        task.getId(), task.getProvider(), task.getAttemptsCount() + 1, task.getMaxAttempts());

                // Simular/ejecutar el reintento
                task.markCompleted();
                retryQueueRepositoryPort.save(task);

                attemptRepositoryPort.save(IntegrationAttempt.ok(
                        task.getTenantId(),
                        task.getProvider(),
                        task.getOperation(),
                        task.getReferenceId(),
                        task.getAttemptsCount() + 1
                ));

                processed++;
            } catch (Exception e) {
                log.warn("Fallo en reintento de tarea {}: {}", task.getId(), e.getMessage());
                task.recordFailure(e.getMessage());
                retryQueueRepositoryPort.save(task);

                attemptRepositoryPort.save(IntegrationAttempt.retry(
                        task.getTenantId(),
                        task.getProvider(),
                        task.getOperation(),
                        task.getReferenceId(),
                        task.getAttemptsCount(),
                        e.getMessage()
                ));
            }
        }

        return processed;
    }
}
