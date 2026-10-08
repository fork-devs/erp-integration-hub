package com.erp.integrationhub.domain.exception;

public class ProviderUnavailableException extends DomainException {
    public ProviderUnavailableException(String message) {
        super(message, "PROVIDER_UNAVAILABLE");
    }
}
