package com.erp.integrationhub.interfaces.rest.response;

import com.erp.integrationhub.domain.model.RetryTask;
import java.time.Instant;
import java.util.UUID;

public record RetryTaskResponse(
        UUID id,
        UUID tenantId,
        String provider,
        String operation,
        UUID referenceId,
        int attemptsCount,
        int maxAttempts,
        Instant nextRetryAt,
        String status,
        String lastError,
        Instant createdAt,
        Instant updatedAt
) {
    public static RetryTaskResponse fromDomain(RetryTask task) {
        return new RetryTaskResponse(
                task.getId(),
                task.getTenantId(),
                task.getProvider().name(),
                task.getOperation(),
                task.getReferenceId(),
                task.getAttemptsCount(),
                task.getMaxAttempts(),
                task.getNextRetryAt(),
                task.getStatus(),
                task.getLastError(),
                task.getCreatedAt(),
                task.getUpdatedAt()
        );
    }
}
