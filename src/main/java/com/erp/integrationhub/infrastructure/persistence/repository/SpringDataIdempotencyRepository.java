package com.erp.integrationhub.infrastructure.persistence.repository;

import com.erp.integrationhub.infrastructure.persistence.entity.IdempotencyKeyJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface SpringDataIdempotencyRepository extends JpaRepository<IdempotencyKeyJpaEntity, UUID> {
    Optional<IdempotencyKeyJpaEntity> findByTenantIdAndScopeAndKey(UUID tenantId, String scope, String key);

    /**
     * Libera las claves en curso que llevan demasiado tiempo reclamadas.
     */
    @Modifying
    @Query("UPDATE IdempotencyKeyJpaEntity k SET k.state = 'RELEASED' "
            + "WHERE k.state = 'IN_PROGRESS' AND k.createdAt < :anterior")
    int releaseStaleProgress(@Param("anterior") java.time.Instant anterior);
}
