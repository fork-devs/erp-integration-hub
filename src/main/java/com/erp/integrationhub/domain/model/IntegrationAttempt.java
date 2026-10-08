package com.erp.integrationhub.domain.model;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

public class IntegrationAttempt implements Serializable {
    private final UUID id;
    private final UUID tenantId;
    private final ProviderType provider;
    private final String operation;
    private final UUID referenceId;
    private final int attemptNumber;
    private final AttemptStatus status;
    private final String errorDetail;
    private final Instant createdAt;

    public IntegrationAttempt(UUID id, UUID tenantId, ProviderType provider, String operation, UUID referenceId, int attemptNumber, AttemptStatus status, String errorDetail, Instant createdAt) {
        this.id = id != null ? id : UUID.randomUUID();
        this.tenantId = tenantId;
        this.provider = provider;
        this.operation = operation;
        this.referenceId = referenceId;
        this.attemptNumber = attemptNumber;
        this.status = status != null ? status : AttemptStatus.OK;
        this.errorDetail = errorDetail;
        this.createdAt = createdAt != null ? createdAt : Instant.now();
    }

    public static IntegrationAttempt ok(UUID tenantId, ProviderType provider, String operation, UUID referenceId, int attemptNumber) {
        return new IntegrationAttempt(UUID.randomUUID(), tenantId, provider, operation, referenceId, attemptNumber, AttemptStatus.OK, null, Instant.now());
    }

    public static IntegrationAttempt retry(UUID tenantId, ProviderType provider, String operation, UUID referenceId, int attemptNumber, String error) {
        return new IntegrationAttempt(UUID.randomUUID(), tenantId, provider, operation, referenceId, attemptNumber, AttemptStatus.RETRY, error, Instant.now());
    }

    public static IntegrationAttempt dlq(UUID tenantId, ProviderType provider, String operation, UUID referenceId, int attemptNumber, String error) {
        return new IntegrationAttempt(UUID.randomUUID(), tenantId, provider, operation, referenceId, attemptNumber, AttemptStatus.DLQ, error, Instant.now());
    }

    public UUID getId() {
        return id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public ProviderType getProvider() {
        return provider;
    }

    public String getOperation() {
        return operation;
    }

    public UUID getReferenceId() {
        return referenceId;
    }

    public int getAttemptNumber() {
        return attemptNumber;
    }

    public AttemptStatus getStatus() {
        return status;
    }

    public String getErrorDetail() {
        return errorDetail;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
