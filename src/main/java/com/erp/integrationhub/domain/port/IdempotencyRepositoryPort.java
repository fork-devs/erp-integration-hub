package com.erp.integrationhub.domain.port;

import com.erp.integrationhub.domain.model.IdempotencyRecord;
import com.erp.integrationhub.domain.model.IdempotencyScope;

import java.util.Optional;
import java.util.UUID;

public interface IdempotencyRepositoryPort {
    Optional<IdempotencyRecord> findByTenantAndScopeAndKey(UUID tenantId, IdempotencyScope scope, String key);
    IdempotencyRecord save(IdempotencyRecord record);
}
