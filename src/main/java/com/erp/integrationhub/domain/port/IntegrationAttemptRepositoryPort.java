package com.erp.integrationhub.domain.port;

import com.erp.integrationhub.domain.model.AttemptStatus;
import com.erp.integrationhub.domain.model.IntegrationAttempt;

import java.util.List;
import java.util.UUID;

public interface IntegrationAttemptRepositoryPort {
    IntegrationAttempt save(IntegrationAttempt attempt);

    /**
     * Counts recorded attempts for an operation.
     * Prevents hardcoded attempt counters to ensure full traceability of retries.
     */
    int countAttempts(UUID tenantId, com.erp.integrationhub.domain.model.ProviderType provider,
                      String operation, UUID referenceId);
    List<IntegrationAttempt> findByTenantId(UUID tenantId, int limit);
    List<IntegrationAttempt> findByTenantIdAndReferenceId(UUID tenantId, UUID referenceId);
    List<IntegrationAttempt> findByTenantIdAndStatus(UUID tenantId, AttemptStatus status, int limit);
}
