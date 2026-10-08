package com.erp.integrationhub.infrastructure.persistence.repository;

import com.erp.integrationhub.infrastructure.persistence.entity.ProviderConfigJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface SpringDataProviderConfigRepository extends JpaRepository<ProviderConfigJpaEntity, UUID> {
    Optional<ProviderConfigJpaEntity> findByProvider(String provider);
}
