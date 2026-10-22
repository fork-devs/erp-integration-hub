package com.erp.integrationhub.interfaces.rest.support;

import com.erp.integrationhub.domain.exception.TenantAccessDeniedException;
import com.erp.integrationhub.domain.exception.TenantContextMissingException;
import com.erp.integrationhub.infrastructure.security.AuthenticatedUser;

import java.util.UUID;

/**
 * Resolucion del taller efectivo de una peticion al hub.
 *
 * <p>La cabecera {@code X-Tenant-ID} es una pista, nunca una autoridad: el
 * taller efectivo procede del principal autenticado. Cuando viene cabecera, se
 * contrasta con el principal y la peticion se rechaza si no coinciden, de
 * modo que declararla no puede ampliar el acceso, solo acotarlo.
 *
 * <p>Ver docs/doc-estado-implementacion.md, hallazgo P1 6.53.
 */
public final class TenantResolver {

    private TenantResolver() {
    }

    /**
     * Taller efectivo de una operacion de negocio. Siempre es un taller
     * concreto: sin el, no hay sobre que operar.
     */
    public static UUID resolveForOperation(UUID requestedTenantId, AuthenticatedUser user) {
        if (user == null) {
            throw new TenantAccessDeniedException(null, requestedTenantId);
        }

        if (requestedTenantId == null) {
            if (user.tenantId() != null) {
                return user.tenantId();
            }
            throw new TenantContextMissingException(
                    "La operacion requiere un taller y el principal no lo aporta.");
        }

        if (!user.canOperateOnTenant(requestedTenantId)) {
            throw new TenantAccessDeniedException(user.tenantId(), requestedTenantId);
        }

        return requestedTenantId;
    }
}
