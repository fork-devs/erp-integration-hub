package com.erp.integrationhub.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
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

    /**
     * El identificador lo ASIGNA la base de datos, no la aplicacion.
     *
     * <p>Con el identificador asignado por el codigo, un guardado de una
     * entidad que ya existe se resolvia como una INSERCIÓN: el registro
     * duplicado violaba la clave primaria, y la actualizacion del resultado
     * de una operacion idempotente era IMPOSIBLE. La actualizacion es
     * justamente lo que hace falta para que el resultado del reintento
     * llegue a la fila que ya existe.
     */
    @Id
    @GeneratedValue
    @org.hibernate.annotations.ColumnDefault("gen_random_uuid()")
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "scope", nullable = false, length = 50)
    private String scope;

    @Column(name = "key_val", nullable = false, length = 255)
    private String key;

    @Column(name = "request_hash", length = 64)
    private String requestHash;

    @Column(name = "state", nullable = false, length = 20)
    private String state;

    @Column(name = "response_hash", length = 255)
    private String responseHash;

    @Column(name = "response_payload", columnDefinition = "TEXT")
    private String responsePayload;

    @Column(name = "created_at")
    private Instant createdAt;

    public IdempotencyKeyJpaEntity() {}

    public IdempotencyKeyJpaEntity(UUID id, UUID tenantId, String scope, String key, String requestHash,
                                    String state, String responseHash, String responsePayload, Instant createdAt) {
        this.id = id;
        this.tenantId = tenantId;
        this.scope = scope;
        this.key = key;
        this.requestHash = requestHash;
        this.state = state;
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

    public String getRequestHash() {
        return requestHash;
    }

    public void setRequestHash(String requestHash) {
        this.requestHash = requestHash;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
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
