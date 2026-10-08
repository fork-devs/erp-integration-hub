package com.erp.integrationhub.domain.exception;

public class TenantAccessDeniedException extends DomainException {
    public TenantAccessDeniedException(String message) {
        super(message, "TENANT_ACCESS_DENIED");
    }
}
