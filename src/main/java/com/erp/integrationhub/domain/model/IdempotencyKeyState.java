package com.erp.integrationhub.domain.model;

import java.io.Serializable;

/**
 * Lifecycle state of an idempotency key.
 *
 * <p>Prevents duplicate execution when concurrent requests arrive and releases keys
 * on transient failures so subsequent retries can proceed safely.
 */
public enum IdempotencyKeyState implements Serializable {

    /**
     * Operation is currently being executed. The key is CLAIMED: concurrent requests
     * with the same key must not contact the downstream provider again.
     */
    IN_PROGRESS,

    /**
     * Operation completed successfully; the stored response payload is definitive.
     */
    COMPLETED,

    /**
     * Operation encountered a transient failure. The key is released so a retry
     * can re-attempt downstream execution.
     */
    RELEASED,

    /**
     * Operation failed definitively. The stored rejection response is final.
     */
    REJECTED;

    /**
     * Indicates whether the stored outcome is final and definitive.
     *
     * @return true if the state is COMPLETED or REJECTED
     */
    public boolean isDefinitiveResult() {
        return this == COMPLETED || this == REJECTED;
    }

    /** Backward compatibility accessor for isDefinitiveResult. */
    public boolean resultadoDefinitivo() {
        return isDefinitiveResult();
    }
}
