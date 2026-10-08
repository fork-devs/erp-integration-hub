package com.erp.integrationhub.application.usecase;

public interface ProcessRetryQueueUseCase {
    int processPendingRetries(int batchSize);
}
