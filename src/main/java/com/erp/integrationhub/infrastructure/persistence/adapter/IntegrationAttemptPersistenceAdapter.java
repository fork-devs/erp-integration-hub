package com.erp.integrationhub.infrastructure.persistence.adapter;

import com.erp.integrationhub.domain.model.AttemptStatus;
import com.erp.integrationhub.domain.model.IntegrationAttempt;
import com.erp.integrationhub.domain.model.ProviderType;
import com.erp.integrationhub.domain.port.IntegrationAttemptRepositoryPort;
import com.erp.integrationhub.infrastructure.persistence.entity.IntegrationAttemptJpaEntity;
import com.erp.integrationhub.infrastructure.persistence.repository.SpringDataIntegrationAttemptRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
public class IntegrationAttemptPersistenceAdapter implements IntegrationAttemptRepositoryPort {

    private final SpringDataIntegrationAttemptRepository repository;

    public IntegrationAttemptPersistenceAdapter(SpringDataIntegrationAttemptRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional
    public IntegrationAttempt save(IntegrationAttempt attempt) {
        IntegrationAttemptJpaEntity entity = toEntity(attempt);
        IntegrationAttemptJpaEntity saved = repository.save(entity);
        return toDomain(saved);
    }

    @Override
    @Transactional
    public List<IntegrationAttempt> findByTenantIdAndReferenceId(UUID tenantId, UUID referenceId) {
        return repository.findByTenantIdAndReferenceId(tenantId, referenceId).stream()
                .map(this::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public List<IntegrationAttempt> findByTenantId(UUID tenantId, int limit) {
        return repository.findByTenantIdOrderByCreatedAtDesc(tenantId, PageRequest.of(0, limit)).stream()
                .map(this::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public List<IntegrationAttempt> findByTenantIdAndStatus(UUID tenantId, AttemptStatus status, int limit) {
        return repository.findByTenantIdAndStatusOrderByCreatedAtDesc(tenantId, status.name(), PageRequest.of(0, limit)).stream()
                .map(this::toDomain)
                .collect(Collectors.toList());
    }

    private IntegrationAttempt toDomain(IntegrationAttemptJpaEntity entity) {
        return new IntegrationAttempt(
                entity.getId(),
                entity.getTenantId(),
                ProviderType.valueOf(entity.getProvider()),
                entity.getOperation(),
                entity.getReferenceId(),
                entity.getAttemptNumber(),
                AttemptStatus.valueOf(entity.getStatus()),
                entity.getErrorDetail(),
                entity.getCreatedAt()
        );
    }

    private IntegrationAttemptJpaEntity toEntity(IntegrationAttempt domain) {
        return new IntegrationAttemptJpaEntity(
                domain.getId(),
                domain.getTenantId(),
                domain.getProvider().name(),
                domain.getOperation(),
                domain.getReferenceId(),
                domain.getAttemptNumber(),
                domain.getStatus().name(),
                domain.getErrorDetail(),
                domain.getCreatedAt()
        );
    }
}
