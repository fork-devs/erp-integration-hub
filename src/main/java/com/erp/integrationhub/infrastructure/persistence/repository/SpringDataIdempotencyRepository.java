package com.erp.integrationhub.infrastructure.persistence.repository;

import com.erp.integrationhub.infrastructure.persistence.entity.IdempotencyKeyJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface SpringDataIdempotencyRepository extends JpaRepository<IdempotencyKeyJpaEntity, UUID> {
    Optional<IdempotencyKeyJpaEntity> findByTenantIdAndScopeAndKey(UUID tenantId, String scope, String key);
}
