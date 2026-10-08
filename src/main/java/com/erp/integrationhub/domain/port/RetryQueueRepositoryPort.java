package com.erp.integrationhub.domain.port;

import com.erp.integrationhub.domain.model.RetryTask;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RetryQueueRepositoryPort {
    RetryTask save(RetryTask task);
    List<RetryTask> findPendingTasks(Instant before, int limit);
    List<RetryTask> findDlqTasks(UUID tenantId, int limit);
    Optional<RetryTask> findById(UUID id);
}
