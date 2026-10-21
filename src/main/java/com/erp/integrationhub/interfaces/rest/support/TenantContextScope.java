package com.erp.integrationhub.interfaces.rest.support;

import com.erp.integrationhub.infrastructure.persistence.rls.TenantContextHolder;

import java.util.UUID;

/**
 * Ambito del contexto de taller de una operacion.
 *
 * <p>El contexto lo establece el principal autenticado y se restaura al
 * terminar la operacion, de modo que no se filtra a la siguiente peticion que
 * reutilice el mismo hilo.
 */
public final class TenantContextScope implements AutoCloseable {

    private final UUID previousTenantId;
    private boolean closed;

    private TenantContextScope(UUID previousTenantId) {
        this.previousTenantId = previousTenantId;
    }

    public static TenantContextScope forTenant(UUID tenantId) {
        UUID previous = TenantContextHolder.getTenantId();
        TenantContextHolder.setTenantId(tenantId);
        return new TenantContextScope(previous);
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        if (previousTenantId == null) {
            TenantContextHolder.clear();
        } else {
            TenantContextHolder.setTenantId(previousTenantId);
        }
    }
}
