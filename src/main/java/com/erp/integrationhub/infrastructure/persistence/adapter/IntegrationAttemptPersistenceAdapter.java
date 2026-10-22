package com.erp.integrationhub.infrastructure.persistence.adapter;

import com.erp.integrationhub.domain.model.AttemptStatus;
import com.erp.integrationhub.domain.model.IntegrationAttempt;
import com.erp.integrationhub.domain.model.ProviderType;
import com.erp.integrationhub.domain.port.IntegrationAttemptRepositoryPort;
import com.erp.integrationhub.infrastructure.persistence.entity.IntegrationAttemptJpaEntity;
import com.erp.integrationhub.infrastructure.persistence.repository.SpringDataIntegrationAttemptRepository;
import org.springframework.data.domain.PageRequest;
import com.erp.integrationhub.infrastructure.persistence.TransactionGuard;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
public class IntegrationAttemptPersistenceAdapter implements IntegrationAttemptRepositoryPort {

    private final TransactionGuard transactionGuard;
    private final SpringDataIntegrationAttemptRepository repository;
    private final com.erp.integrationhub.infrastructure.persistence.rls.PlatformScopeQueryExecutor
            platformScopeQueryExecutor;

    public IntegrationAttemptPersistenceAdapter(
            SpringDataIntegrationAttemptRepository repository,
            com.erp.integrationhub.infrastructure.persistence.rls.PlatformScopeQueryExecutor platformScopeQueryExecutor,
            TransactionGuard transactionGuard) {
        this.repository = repository;
        this.platformScopeQueryExecutor = platformScopeQueryExecutor;
        this.transactionGuard = transactionGuard;
    }

    @Override
    public IntegrationAttempt save(IntegrationAttempt attempt) {
        transactionGuard.requireActiveTransaction("saveIntegrationAttempt");
        IntegrationAttemptJpaEntity entity = toEntity(attempt);
        IntegrationAttemptJpaEntity saved = repository.save(entity);
        return toDomain(saved);
    }

    /**
     * Registra el intento con el rol de plataforma.
     *
     * <p>El relay de reintentos registra el intento de una operacion de un
     * taller que no es el suyo: con el rol de aplicacion, la escritura
     * violaria la politica de aislamiento y el relay fallaria al registrar el
     * intento, dejando la tarea sin trazabilidad.
     */
    public void saveWithPlatformScope(IntegrationAttempt attempt) {
        platformScopeQueryExecutor.insert(
                "INSERT INTO integration_attempts (id, tenant_id, provider, operation, reference_id, "
                        + "attempt_number, status, error_detail, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                attempt.getId(),
                attempt.getTenantId(),
                attempt.getProvider().name(),
                attempt.getOperation(),
                attempt.getReferenceId(),
                attempt.getAttemptNumber(),
                attempt.getStatus().name(),
                attempt.getErrorDetail(),
                Timestamp.from(attempt.getCreatedAt() != null ? attempt.getCreatedAt() : java.time.Instant.now()));
    }

    @Override
    @Transactional(readOnly = true)
    public int countAttempts(UUID tenantId, ProviderType provider, String operation, UUID referenceId) {
        return repository.countByTenantIdAndProviderAndOperationAndReferenceId(
                tenantId, provider.name(), operation, referenceId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<IntegrationAttempt> findByTenantIdAndReferenceId(UUID tenantId, UUID referenceId) {
        return repository.findByTenantIdAndReferenceId(tenantId, referenceId).stream()
                .map(this::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<IntegrationAttempt> findByTenantId(UUID tenantId, int limit) {
        return repository.findByTenantIdOrderByCreatedAtDesc(tenantId, PageRequest.of(0, limit)).stream()
                .map(this::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
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
