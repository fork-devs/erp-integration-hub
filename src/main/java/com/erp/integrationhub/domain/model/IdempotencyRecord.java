package com.erp.integrationhub.domain.model;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

/**
 * Registro de una clave de idempotencia.
 *
 * <p>Lleva la HUELLA de la carga util de la peticion, no la de la
 * respuesta. La huella de la carga util es lo que distingue "la misma
 * peticion repetida" de "otra peticion con la misma clave": sin ella, el
 * sistema devolvia el resultado de la primera y el cobro de la segunda no se
 * hacia.
 *
 * <p>La huella es CRIPTOGRAFICA (SHA-256). Con una huella de 32 bits, dos
 * cargas utiles distintas la comparten con una probabilidad alta, y la
 * comprobacion daria un falso "es la misma peticion".
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
            throw new IllegalArgumentException("tenantId, scope y key son obligatorios para IdempotencyRecord");
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
     * Registro en curso: la clave queda reclamada antes de contactar al
     * proveedor.
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
     * El registro pasa a COMPLETED, con su resultado. La transicion es
     * valida solo desde IN_PROGRESS o desde un estado ya definitivo, en el
     * que se conserva el resultado anterior.
     */
    public IdempotencyRecord completed(String responsePayload, String responseHash) {
        return new IdempotencyRecord(id, tenantId, scope, key, requestHash, responseHash, responsePayload,
                IdempotencyKeyState.COMPLETED, createdAt);
    }

    /**
     * El registro pasa a RELEASED: la operacion fallo de forma transitoria y
     * la clave se libera para un reintento.
     *
     * <p>El resultado anterior se conserva, pero deja de ser DEFINITIVO: una
     * peticion con la misma clave no debe recibirlo como si lo fuera.
     */
    public IdempotencyRecord released() {
        return new IdempotencyRecord(id, tenantId, scope, key, requestHash, responseHash, responsePayload,
                IdempotencyKeyState.RELEASED, createdAt);
    }

    /**
     * El registro pasa a REJECTED: la operacion fallo de forma definitiva y
     * el resultado almacenado es definitivo.
     */
    public IdempotencyRecord rejected(String responsePayload, String responseHash) {
        return new IdempotencyRecord(id, tenantId, scope, key, requestHash, responseHash, responsePayload,
                IdempotencyKeyState.REJECTED, createdAt);
    }

    /**
     * Indica si el registro corresponde a la MISMA peticion que la huella
     * dada.
     */
    public boolean mismaPeticion(String huella) {
        return requestHash != null && requestHash.equals(huella);
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
