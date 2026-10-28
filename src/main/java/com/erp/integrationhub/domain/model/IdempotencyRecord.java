package com.erp.integrationhub.domain.model;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

/**
 * Record holding state and request/response payloads for an idempotency key.
 *
 * <p>Tracks cryptographic SHA-256 fingerprint of the request payload to distinguish
 * identical repeated requests from different requests inadvertently sharing the same key.
 */
public class IdempotencyRecord implements Serializable {

    private final UUID id;
    private final UUID tenantId;
    private final IdempotencyScope scope;
    private final String key;
    private final String requestHash;
    private final String responseHash;
    private final String responsePayload;
    private final IdempotencyKeyState state;
    private final Instant createdAt;

    public IdempotencyRecord(UUID id,
                              UUID tenantId,
                              IdempotencyScope scope,
                              String key,
                              String requestHash,
                              String responseHash,
                              String responsePayload,
                              IdempotencyKeyState state,
                              Instant createdAt) {
        if (tenantId == null || scope == null || key == null || key.trim().isEmpty()) {
            throw new IllegalArgumentException("tenantId, scope, and key are required for IdempotencyRecord");
        }
        this.id = id != null ? id : UUID.randomUUID();
        this.tenantId = tenantId;
        this.scope = scope;
        this.key = key.trim();
        this.requestHash = requestHash;
        this.responseHash = responseHash;
        this.responsePayload = responsePayload;
        this.state = state != null ? state : IdempotencyKeyState.COMPLETED;
        this.createdAt = createdAt != null ? createdAt : Instant.now();
    }

    /**
     * Creates an in-progress record claiming the idempotency key prior to external invocation.
     *
     * @param tenantId workshop/tenant ID
     * @param scope operational scope (PAYMENT, VOUCHER)
     * @param key unique idempotency key
     * @param requestHash SHA-256 fingerprint of the request payload
     * @return in-progress record
     */
    public static IdempotencyRecord claiming(UUID tenantId,
                                             IdempotencyScope scope,
                                             String key,
                                             String requestHash) {
        return new IdempotencyRecord(null, tenantId, scope, key, requestHash, null, null,
                IdempotencyKeyState.IN_PROGRESS, Instant.now());
    }

    public static IdempotencyRecord of(UUID tenantId, IdempotencyScope scope, String key, String responsePayload) {
        String hash = responsePayload != null
                ? Integer.toHexString(responsePayload.hashCode())
                : null;
        return new IdempotencyRecord(UUID.randomUUID(), tenantId, scope, key, null, hash, responsePayload,
                IdempotencyKeyState.COMPLETED, Instant.now());
    }

    /**
     * Transitions record to COMPLETED with stored response payload and hash.
     *
     * @param responsePayload serialized response body
     * @param responseHash response digest
     * @return updated record
     */
    public IdempotencyRecord completed(String responsePayload, String responseHash) {
        return new IdempotencyRecord(id, tenantId, scope, key, requestHash, responseHash, responsePayload,
                IdempotencyKeyState.COMPLETED, createdAt);
    }

    /**
     * Transitions record to RELEASED on transient failure, allowing retries.
     *
     * @return updated record with RELEASED state
     */
    public IdempotencyRecord released() {
        return new IdempotencyRecord(id, tenantId, scope, key, requestHash, responseHash, responsePayload,
                IdempotencyKeyState.RELEASED, createdAt);
    }

    /**
     * Transitions record to REJECTED on definitive failure.
     *
     * @param responsePayload rejection response body
     * @param responseHash rejection response digest
     * @return updated record with REJECTED state
     */
    public IdempotencyRecord rejected(String responsePayload, String responseHash) {
        return new IdempotencyRecord(id, tenantId, scope, key, requestHash, responseHash, responsePayload,
                IdempotencyKeyState.REJECTED, createdAt);
    }

    /**
     * Determines whether this idempotency record belongs to the same request payload fingerprint.
     *
     * @param fingerprint SHA-256 fingerprint of current incoming request
     * @return true if fingerprints match exactly
     */
    public boolean isSameRequest(String fingerprint) {
        return requestHash != null && requestHash.equals(fingerprint);
    }

    /** Backward compatibility accessor for isSameRequest. */
    public boolean mismaPeticion(String huella) {
        return isSameRequest(huella);
    }

    public UUID getId() {
        return id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public IdempotencyScope getScope() {
        return scope;
    }

    public String getKey() {
        return key;
    }

    public String getRequestHash() {
        return requestHash;
    }

    public String getResponseHash() {
        return responseHash;
    }

    public String getResponsePayload() {
        return responsePayload;
    }

    public IdempotencyKeyState getState() {
        return state;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
