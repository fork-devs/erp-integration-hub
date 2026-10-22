package com.erp.integrationhub.infrastructure.persistence.rls;

import java.util.UUID;

/**
 * Contexto de taller de la peticion en curso.
 *
 * <p>Lo establece el principal autenticado, jamas la fila que se va a leer o
 * escribir. Esa distincion es la que hace que la politica de Row-Level
 * Security sirva de algo: si el contexto lo fijase el propio dato, la
 * comprobacion seria siempre satisfacida.
 *
 * <p>Ver docs/doc-estado-implementacion.md, hallazgo P1 6.53.
 */
public final class TenantContextHolder {

    private static final ThreadLocal<UUID> CURRENT_TENANT = new ThreadLocal<>();

    private TenantContextHolder() {
    }

    public static void setTenantId(UUID tenantId) {
        CURRENT_TENANT.set(tenantId);
    }

    public static UUID getTenantId() {
        return CURRENT_TENANT.get();
    }

    public static void clear() {
        CURRENT_TENANT.remove();
    }
}
