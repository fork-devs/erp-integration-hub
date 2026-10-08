package com.erp.integrationhub.interfaces.rest.response;

import com.erp.integrationhub.domain.model.IntegrationAttempt;
import java.time.Instant;
import java.util.UUID;

public record IntegrationAttemptResponse(
        UUID id,
        UUID tenantId,
        String provider,
        String operation,
        UUID referenceId,
        int attemptNumber,
        String status,
        String errorDetail,
        Instant createdAt
) {
    public static IntegrationAttemptResponse fromDomain(IntegrationAttempt attempt) {
        return new IntegrationAttemptResponse(
                attempt.getId(),
                attempt.getTenantId(),
                attempt.getProvider().name(),
                attempt.getOperation(),
                attempt.getReferenceId(),
                attempt.getAttemptNumber(),
                attempt.getStatus().name(),
                attempt.getErrorDetail(),
                attempt.getCreatedAt()
        );
    }
}
