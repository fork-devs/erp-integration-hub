package com.erp.integrationhub.domain.exception;

public class MaxRetriesExceededException extends DomainException {
    public MaxRetriesExceededException(String message) {
        super(message, "MAX_RETRIES_EXCEEDED_MOVED_TO_DLQ");
    }
}
