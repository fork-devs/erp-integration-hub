package com.erp.integrationhub.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "idempotency_keys",
        uniqueConstraints = @UniqueConstraint(columnNames = {"tenant_id", "scope", "key_val"})
)
public class IdempotencyKeyJpaEntity {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "scope", nullable = false, length = 50)
    private String scope;

    @Column(name = "key_val", nullable = false, length = 255)
    private String key;

    @Column(name = "response_hash", length = 255)
    private String responseHash;

    @Column(name = "response_payload", columnDefinition = "TEXT")
    private String responsePayload;

    @Column(name = "created_at")
    private Instant createdAt;

    public IdempotencyKeyJpaEntity() {}

    public IdempotencyKeyJpaEntity(UUID id, UUID tenantId, String scope, String key, String responseHash, String responsePayload, Instant createdAt) {
        this.id = id;
        this.tenantId = tenantId;
        this.scope = scope;
        this.key = key;
        this.responseHash = responseHash;
        this.responsePayload = responsePayload;
        this.createdAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public void setTenantId(UUID tenantId) {
        this.tenantId = tenantId;
    }

    public String getScope() {
        return scope;
    }

    public void setScope(String scope) {
        this.scope = scope;
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public String getResponseHash() {
        return responseHash;
    }

    public void setResponseHash(String responseHash) {
        this.responseHash = responseHash;
    }

    public String getResponsePayload() {
        return responsePayload;
    }

    public void setResponsePayload(String responsePayload) {
        this.responsePayload = responsePayload;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
