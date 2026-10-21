package com.erp.integrationhub.domain.exception;

import java.util.UUID;

/**
 * Se lanza cuando un principal intenta operar sobre un taller distinto del
 * suyo y no tiene alcance transversal.
 *
 * <p>Ver docs/doc-estado-implementacion.md, hallazgo P1 6.53.
 */
public class TenantAccessDeniedException extends DomainException {

    public TenantAccessDeniedException(String message) {
        super(message, "TENANT_ACCESS_DENIED");
    }

    public TenantAccessDeniedException(UUID tenantDelPrincipal, UUID tallerSolicitado) {
        super("El taller " + tallerSolicitado + " no es accesible desde el principal actual.",
                "TENANT_ACCESS_DENIED");
    }
}
