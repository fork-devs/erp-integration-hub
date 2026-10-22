package com.erp.integrationhub.infrastructure.persistence.repository;

import com.erp.integrationhub.infrastructure.persistence.entity.ProcessedWebhookJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface SpringDataProcessedWebhookRepository extends JpaRepository<ProcessedWebhookJpaEntity, UUID> {
}
