package com.erp.integrationhub.infrastructure.persistence.adapter;

import com.erp.integrationhub.domain.model.IdempotencyRecord;
import com.erp.integrationhub.domain.model.IdempotencyScope;
import com.erp.integrationhub.domain.port.IdempotencyRepositoryPort;
import com.erp.integrationhub.infrastructure.persistence.entity.IdempotencyKeyJpaEntity;
import com.erp.integrationhub.infrastructure.persistence.repository.SpringDataIdempotencyRepository;
import com.erp.integrationhub.infrastructure.persistence.TransactionGuard;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Component
public class IdempotencyPersistenceAdapter implements IdempotencyRepositoryPort {

    private final TransactionGuard transactionGuard;
    private final SpringDataIdempotencyRepository repository;

    public IdempotencyPersistenceAdapter(SpringDataIdempotencyRepository repository,
                                       TransactionGuard transactionGuard) {
        this.repository = repository;
        this.transactionGuard = transactionGuard;
    }

    @Override
    @Transactional
    public Optional<IdempotencyRecord> findByTenantAndScopeAndKey(UUID tenantId, IdempotencyScope scope, String key) {
        return repository.findByTenantIdAndScopeAndKey(tenantId, scope.name(), key).map(this::toDomain);
    }

    @Override
    public IdempotencyRecord save(IdempotencyRecord record) {
        transactionGuard.requireActiveTransaction("saveIdempotencyRecord");
        IdempotencyKeyJpaEntity entity = toEntity(record);
        IdempotencyKeyJpaEntity saved = repository.save(entity);
        return toDomain(saved);
    }

    private IdempotencyRecord toDomain(IdempotencyKeyJpaEntity entity) {
        return new IdempotencyRecord(
                entity.getId(),
                entity.getTenantId(),
                IdempotencyScope.valueOf(entity.getScope()),
                entity.getKey(),
                entity.getResponseHash(),
                entity.getResponsePayload(),
                entity.getCreatedAt()
        );
    }

    private IdempotencyKeyJpaEntity toEntity(IdempotencyRecord domain) {
        return new IdempotencyKeyJpaEntity(
                domain.getId(),
                domain.getTenantId(),
                domain.getScope().name(),
                domain.getKey(),
                domain.getResponseHash(),
                domain.getResponsePayload(),
                domain.getCreatedAt()
        );
    }
}
