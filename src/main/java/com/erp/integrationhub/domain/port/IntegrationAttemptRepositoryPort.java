package com.erp.integrationhub.domain.port;

import com.erp.integrationhub.domain.model.AttemptStatus;
import com.erp.integrationhub.domain.model.IntegrationAttempt;

import java.util.List;
import java.util.UUID;

public interface IntegrationAttemptRepositoryPort {
    IntegrationAttempt save(IntegrationAttempt attempt);
    List<IntegrationAttempt> findByTenantId(UUID tenantId, int limit);
    List<IntegrationAttempt> findByTenantIdAndReferenceId(UUID tenantId, UUID referenceId);
    List<IntegrationAttempt> findByTenantIdAndStatus(UUID tenantId, AttemptStatus status, int limit);
}
