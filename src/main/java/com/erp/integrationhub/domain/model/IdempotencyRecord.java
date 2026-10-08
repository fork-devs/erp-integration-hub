package com.erp.integrationhub.domain.model;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

public class IdempotencyRecord implements Serializable {
    private final UUID id;
    private final UUID tenantId;
    private final IdempotencyScope scope;
    private final String key;
    private final String responseHash;
    private final String responsePayload;
    private final Instant createdAt;

    public IdempotencyRecord(UUID id, UUID tenantId, IdempotencyScope scope, String key, String responseHash, String responsePayload, Instant createdAt) {
        if (tenantId == null || scope == null || key == null || key.trim().isEmpty()) {
            throw new IllegalArgumentException("tenantId, scope y key son obligatorios para IdempotencyRecord");
        }
        this.id = id != null ? id : UUID.randomUUID();
        this.tenantId = tenantId;
        this.scope = scope;
        this.key = key.trim();
        this.responseHash = responseHash;
        this.responsePayload = responsePayload;
        this.createdAt = createdAt != null ? createdAt : Instant.now();
    }

    public static IdempotencyRecord of(UUID tenantId, IdempotencyScope scope, String key, String responsePayload) {
        String hash = responsePayload != null ? Integer.toHexString(responsePayload.hashCode()) : null;
        return new IdempotencyRecord(UUID.randomUUID(), tenantId, scope, key, hash, responsePayload, Instant.now());
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

    public String getResponseHash() {
        return responseHash;
    }

    public String getResponsePayload() {
        return responsePayload;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
