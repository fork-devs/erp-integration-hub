package com.erp.integrationhub.domain.exception;

public class IdempotencyConflictException extends DomainException {
    public IdempotencyConflictException(String message) {
        super(message, "IDEMPOTENCY_KEY_CONFLICT");
    }
}
