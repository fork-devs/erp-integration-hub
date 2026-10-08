package com.erp.integrationhub.infrastructure.scheduler;

import com.erp.integrationhub.application.usecase.ProcessRetryQueueUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "erp.integration.retry.enabled", havingValue = "true", matchIfMissing = true)
public class IntegrationRetryQueueScheduler {

    private static final Logger log = LoggerFactory.getLogger(IntegrationRetryQueueScheduler.class);

    private final ProcessRetryQueueUseCase processRetryQueueUseCase;

    public IntegrationRetryQueueScheduler(ProcessRetryQueueUseCase processRetryQueueUseCase) {
        this.processRetryQueueUseCase = processRetryQueueUseCase;
    }

    @Scheduled(fixedDelayString = "${erp.integration.retry.interval-ms:5000}")
    public void runRetryQueueRelay() {
        try {
            int processed = processRetryQueueUseCase.processPendingRetries(20);
            if (processed > 0) {
                log.info("Procesadas {} tareas pendientes de reintento en integration-hub", processed);
            }
        } catch (Exception e) {
            log.error("Error al procesar la cola de reintentos: {}", e.getMessage());
        }
    }
}
