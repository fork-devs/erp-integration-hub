package com.erp.integrationhub.infrastructure.persistence.adapter;

import com.erp.integrationhub.domain.model.ProviderType;
import com.erp.integrationhub.domain.model.RetryTask;
import com.erp.integrationhub.domain.port.RetryQueueRepositoryPort;
import com.erp.integrationhub.infrastructure.persistence.entity.RetryTaskJpaEntity;
import com.erp.integrationhub.infrastructure.persistence.repository.SpringDataRetryTaskRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
public class RetryQueuePersistenceAdapter implements RetryQueueRepositoryPort {

    private final SpringDataRetryTaskRepository repository;

    public RetryQueuePersistenceAdapter(SpringDataRetryTaskRepository repository) {
        this.repository = repository;
    }

    @Override
    public RetryTask save(RetryTask task) {
        RetryTaskJpaEntity entity = toEntity(task);
        RetryTaskJpaEntity saved = repository.save(entity);
        return toDomain(saved);
    }

    @Override
    public List<RetryTask> findPendingTasks(Instant before, int limit) {
        return repository.findPendingTasksDue(before, PageRequest.of(0, limit)).stream()
                .map(this::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public List<RetryTask> findDlqTasks(UUID tenantId, int limit) {
        return repository.findByTenantIdAndStatusOrderByCreatedAtDesc(tenantId, "DLQ", PageRequest.of(0, limit)).stream()
                .map(this::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public Optional<RetryTask> findById(UUID id) {
        return repository.findById(id).map(this::toDomain);
    }

    private RetryTask toDomain(RetryTaskJpaEntity entity) {
        return new RetryTask(
                entity.getId(),
                entity.getTenantId(),
                ProviderType.valueOf(entity.getProvider()),
                entity.getOperation(),
                entity.getReferenceId(),
                entity.getPayload(),
                entity.getAttemptsCount() != null ? entity.getAttemptsCount() : 0,
                entity.getMaxAttempts() != null ? entity.getMaxAttempts() : 5,
                entity.getNextRetryAt(),
                entity.getStatus(),
                entity.getLastError(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    private RetryTaskJpaEntity toEntity(RetryTask domain) {
        return new RetryTaskJpaEntity(
                domain.getId(),
                domain.getTenantId(),
                domain.getProvider().name(),
                domain.getOperation(),
                domain.getReferenceId(),
                domain.getPayload(),
                domain.getAttemptsCount(),
                domain.getMaxAttempts(),
                domain.getNextRetryAt(),
                domain.getStatus(),
                domain.getLastError(),
                domain.getCreatedAt(),
                domain.getUpdatedAt()
        );
    }
}
