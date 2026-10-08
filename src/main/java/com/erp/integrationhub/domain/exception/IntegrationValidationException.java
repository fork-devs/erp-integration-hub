package com.erp.integrationhub.domain.exception;

public class IntegrationValidationException extends DomainException {
    public IntegrationValidationException(String message) {
        super(message, "INTEGRATION_VALIDATION_ERROR");
    }
}
