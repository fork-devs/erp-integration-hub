package com.erp.integrationhub.application.service;

import com.erp.integrationhub.domain.exception.RetryTaskNotFoundException;
import com.erp.integrationhub.domain.model.RetryTask;
import com.erp.integrationhub.domain.port.RetryQueueRepositoryPort;
import com.erp.integrationhub.domain.port.UnitOfWork;
import com.erp.integrationhub.domain.usecase.ReactivateRetryTaskUseCase;

import java.util.UUID;

/**
 * Application service for reactivating Dead Letter Queue (DLQ) tasks back to the retry queue.
 */
public class RetryTaskReactivationService implements ReactivateRetryTaskUseCase {

    private final RetryQueueRepositoryPort retryQueueRepositoryPort;
    private final UnitOfWork unitOfWork;

    public RetryTaskReactivationService(RetryQueueRepositoryPort retryQueueRepositoryPort, UnitOfWork unitOfWork) {
        this.retryQueueRepositoryPort = retryQueueRepositoryPort;
        this.unitOfWork = unitOfWork;
    }

    @Override
    public RetryTask reactivate(UUID taskId) {
        RetryTask task = retryQueueRepositoryPort.findById(taskId)
                .orElseThrow(() -> new RetryTaskNotFoundException(taskId));

        task.reactivate();
        return unitOfWork.callInTransaction(() -> retryQueueRepositoryPort.save(task));
    }
}
