package com.erp.integrationhub.infrastructure.persistence.repository;

import com.erp.integrationhub.infrastructure.persistence.entity.IntegrationAttemptJpaEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface SpringDataIntegrationAttemptRepository extends JpaRepository<IntegrationAttemptJpaEntity, UUID> {

    int countByTenantIdAndProviderAndOperationAndReferenceId(UUID tenantId, String provider,
                                                             String operation, UUID referenceId);
    List<IntegrationAttemptJpaEntity> findByTenantIdOrderByCreatedAtDesc(UUID tenantId, Pageable pageable);
    List<IntegrationAttemptJpaEntity> findByTenantIdAndReferenceId(UUID tenantId, UUID referenceId);
    List<IntegrationAttemptJpaEntity> findByTenantIdAndStatusOrderByCreatedAtDesc(UUID tenantId, String status, Pageable pageable);
}
