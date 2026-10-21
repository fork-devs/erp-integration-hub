package com.erp.integrationhub.domain.exception;

/**
 * Se lanza cuando una operacion que requiere un taller concreto se ejecuta
 * sin que el principal lo aporte.
 *
 * <p>Ver docs/doc-estado-implementacion.md, hallazgo P1 6.53.
 */
public class TenantContextMissingException extends DomainException {

    public TenantContextMissingException() {
        this("La operacion requiere un taller y el principal no lo aporta.");
    }

    public TenantContextMissingException(String message) {
        super(message, "TENANT_CONTEXT_MISSING");
    }
}
