package com.erp.integrationhub.infrastructure.persistence.repository;

import com.erp.integrationhub.infrastructure.persistence.entity.RetryTaskJpaEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface SpringDataRetryTaskRepository extends JpaRepository<RetryTaskJpaEntity, UUID> {

    @Query("SELECT r FROM RetryTaskJpaEntity r WHERE r.status = 'PENDING' AND r.nextRetryAt <= :now ORDER BY r.nextRetryAt ASC")
    List<RetryTaskJpaEntity> findPendingTasksDue(@Param("now") Instant now, Pageable pageable);

    List<RetryTaskJpaEntity> findByTenantIdAndStatusOrderByCreatedAtDesc(UUID tenantId, String status, Pageable pageable);
}
