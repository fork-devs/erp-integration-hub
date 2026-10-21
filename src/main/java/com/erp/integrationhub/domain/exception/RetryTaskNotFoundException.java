package com.erp.integrationhub.domain.exception;

import java.util.UUID;

public class RetryTaskNotFoundException extends DomainException {

    public RetryTaskNotFoundException(UUID taskId) {
        super("La tarea de reintento " + taskId + " no existe", "RETRY_TASK_NOT_FOUND");
    }
}
