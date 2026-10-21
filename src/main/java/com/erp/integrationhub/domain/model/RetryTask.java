package com.erp.integrationhub.domain.model;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

public class RetryTask implements Serializable {
    private final UUID id;
    private final UUID tenantId;
    private final ProviderType provider;
    private final String operation;
    private final UUID referenceId;
    private final String payload;
    private int attemptsCount;
    private final int maxAttempts;
    private Instant nextRetryAt;
    private String status; // PENDING, PROCESSING, COMPLETED, DLQ
    private String lastError;
    private final Instant createdAt;
    private Instant updatedAt;

    public RetryTask(
            UUID id,
            UUID tenantId,
            ProviderType provider,
            String operation,
            UUID referenceId,
            String payload,
            int attemptsCount,
            int maxAttempts,
            Instant nextRetryAt,
            String status,
            String lastError,
            Instant createdAt,
            Instant updatedAt) {
        this.id = id != null ? id : UUID.randomUUID();
        this.tenantId = tenantId;
        this.provider = provider;
        this.operation = operation;
        this.referenceId = referenceId;
        this.payload = payload;
        this.attemptsCount = attemptsCount;
        this.maxAttempts = maxAttempts > 0 ? maxAttempts : 5;
        this.nextRetryAt = nextRetryAt != null ? nextRetryAt : Instant.now();
        this.status = status != null ? status : "PENDING";
        this.lastError = lastError;
        this.createdAt = createdAt != null ? createdAt : Instant.now();
        this.updatedAt = updatedAt != null ? updatedAt : Instant.now();
    }

    public static RetryTask create(UUID tenantId, ProviderType provider, String operation, UUID referenceId, String payload, int maxAttempts) {
        return new RetryTask(
                UUID.randomUUID(),
                tenantId,
                provider,
                operation,
                referenceId,
                payload,
                0,
                maxAttempts,
                Instant.now().plusSeconds(5), // Primer reintento en 5 segundos
                "PENDING",
                null,
                Instant.now(),
                Instant.now()
        );
    }

    public void recordFailure(String error) {
        this.attemptsCount++;
        this.lastError = error;
        this.updatedAt = Instant.now();

        if (this.attemptsCount >= this.maxAttempts) {
            this.status = "DLQ"; // Derivado a Dead Letter Queue
        } else {
            this.status = "PENDING";
            // Exponential Backoff: 5s, 30s, 5m (300s), 30m (1800s), 2h (7200s)
            long backoffSeconds = calculateBackoffSeconds(this.attemptsCount);
            this.nextRetryAt = Instant.now().plusSeconds(backoffSeconds);
        }
    }

    public void markCompleted() {
        this.status = "COMPLETED";
        this.updatedAt = Instant.now();
    }

    /**
     * Mueve la tarea a la cola de muertos, que es un estado TERMINAL: la
     * tarea no se reintentara sola.
     *
     * <p>Se usa cuando la operacion con el proveedor YA se llevo a cabo, pero
     * el registro local no pudo escribirse. Reintentar la tarea reenviaria la
     * operacion al proveedor, que ya la acepto: el resultado seria una
     * operacion duplicada. Es preferible una tarea que exige atencion
     * manual a un duplicado.
     */
    public void markDeadLetter(String reason) {
        this.status = "DLQ";
        this.lastError = reason;
        this.updatedAt = Instant.now();
    }

    private long calculateBackoffSeconds(int attempt) {
        return switch (attempt) {
            case 1 -> 5L;
            case 2 -> 30L;
            case 3 -> 300L;    // 5 min
            case 4 -> 1800L;   // 30 min
            default -> 7200L;  // 2 horas (hasta 24h acumuladas)
        };
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

    public String getPayload() {
        return payload;
    }

    public int getAttemptsCount() {
        return attemptsCount;
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }

    public Instant getNextRetryAt() {
        return nextRetryAt;
    }

    public String getStatus() {
        return status;
    }

    public String getLastError() {
        return lastError;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
