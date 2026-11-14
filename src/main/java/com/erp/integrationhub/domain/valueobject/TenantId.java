package com.erp.integrationhub.domain.valueobject;

import java.util.UUID;

/**
 * Strongly-typed tenant identifier that rejects null and the nil UUID.
 */
public record TenantId(UUID value) {

    public static final UUID NIL_UUID = new UUID(0L, 0L);

    public TenantId {
        if (value == null) {
            throw new IllegalArgumentException("TenantId cannot be null");
        }
        if (NIL_UUID.equals(value)) {
            throw new IllegalArgumentException("TenantId cannot be the nil UUID");
        }
    }

    /**
     * Parses a trimmed UUID string and rejects null, blank, malformed, or nil UUID values.
     */
    public static TenantId fromString(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("TenantId string cannot be null or blank");
        }
        try {
            return new TenantId(UUID.fromString(raw.trim()));
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Invalid TenantId UUID format: " + raw, ex);
        }
    }

    /**
     * Generates a new random TenantId.
     */
    public static TenantId generate() {
        return new TenantId(UUID.randomUUID());
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
