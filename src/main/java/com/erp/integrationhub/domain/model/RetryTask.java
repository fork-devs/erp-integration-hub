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

    /**
     * Devuelve la tarea a la cola de pendientes, con el contador de intentos
     * restablecido.
     *
     * <p>Se usa desde la cola de muertos: la tarea ya no se reintentaba sola,
     * y un operador la reactiva cuando la causa del fallo se ha resuelto.
     */
    public void reactivate() {
        this.attemptsCount = 0;
        this.status = "PENDING";
        this.lastError = null;
        this.nextRetryAt = Instant.now();
        this.updatedAt = Instant.now();
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

    /**
     * Escala de esperas, en segundos. Es CONFIGURABLE: la ventana total de
     * reintentos depende de ella, y una ventana fija no sirve para los
     * proveedores con caracteristicas distintas.
     *
     * <p>La escala por defecto suma unas 23,6 horas, que es la ventana de
     * recuperacion que el diseno del proyecto fija para una caida de
     * proveedor.
     */
    public static final long[] ESCALA_POR_DEFECTO = {5, 30, 300, 1800, 10800, 28800, 45000};

    private long calculateBackoffSeconds(int attempt) {
        long[] escala = ESCALA_POR_DEFECTO;
        int indice = Math.min(Math.max(attempt - 1, 0), escala.length - 1);
        return escala[indice];
    }

    /**
     * Suma de las esperas de la escala: la ventana total de reintentos.
     */
    public static long ventanaTotalSegundos(long[] escala) {
        long total = 0;
        for (long espera : escala) {
            total += espera;
        }
        return total;
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
