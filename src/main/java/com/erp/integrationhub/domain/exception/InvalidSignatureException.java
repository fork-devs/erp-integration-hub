package com.erp.integrationhub.domain.exception;

public class InvalidSignatureException extends DomainException {
    public InvalidSignatureException(String message) {
        super(message, "INVALID_WEBHOOK_SIGNATURE");
    }
}
