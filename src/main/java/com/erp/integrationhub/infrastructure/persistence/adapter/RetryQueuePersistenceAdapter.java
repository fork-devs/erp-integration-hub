package com.erp.integrationhub.infrastructure.persistence.adapter;

import com.erp.integrationhub.domain.model.ProviderType;
import com.erp.integrationhub.domain.model.RetryTask;
import com.erp.integrationhub.domain.port.RetryQueueRepositoryPort;
import com.erp.integrationhub.infrastructure.persistence.entity.RetryTaskJpaEntity;
import com.erp.integrationhub.infrastructure.persistence.rls.PlatformScopeQueryExecutor;
import com.erp.integrationhub.infrastructure.persistence.repository.SpringDataRetryTaskRepository;
import org.springframework.data.domain.PageRequest;
import com.erp.integrationhub.infrastructure.persistence.TransactionGuard;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
public class RetryQueuePersistenceAdapter implements RetryQueueRepositoryPort {

    private final TransactionGuard transactionGuard;
    private final SpringDataRetryTaskRepository repository;
    private final PlatformScopeQueryExecutor platformScopeQueryExecutor;

    public RetryQueuePersistenceAdapter(SpringDataRetryTaskRepository repository,
                                        PlatformScopeQueryExecutor platformScopeQueryExecutor,
                                        TransactionGuard transactionGuard) {
        this.repository = repository;
        this.platformScopeQueryExecutor = platformScopeQueryExecutor;
        this.transactionGuard = transactionGuard;
    }

    @Override
    public RetryTask save(RetryTask task) {
        transactionGuard.requireActiveTransaction("saveRetryTask");
        RetryTaskJpaEntity entity = toEntity(task);
        RetryTaskJpaEntity saved = repository.save(entity);
        return toDomain(saved);
    }

    @Override
    @Transactional
    public List<RetryTask> findPendingTasks(Instant before, int limit) {
        // El relay recorre la cola de pendientes de TODOS los talleres: es un
        // proceso interno de la plataforma, no una operacion de un taller, y
        // todavia no conoce el taller de cada mensaje. La consulta se ejecuta
        // con el rol de plataforma, el unico con permiso transversal, y con
        // una credencial separada de la de la aplicacion.
        return platformScopeQueryExecutor.query(
                "SELECT * FROM integration_retry_tasks "
                        + "WHERE status = 'PENDING' AND next_retry_at <= ? "
                        + "ORDER BY next_retry_at ASC LIMIT ?",
                Timestamp.from(before), limit).stream()
                .map(this::toDomainFromMap)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public List<RetryTask> findDlqTasks(UUID tenantId, int limit) {
        return repository.findByTenantIdAndStatusOrderByCreatedAtDesc(tenantId, "DLQ", PageRequest.of(0, limit)).stream()
                .map(this::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public Optional<RetryTask> findById(UUID id) {
        return repository.findById(id).map(this::toDomain);
    }

    /**
     * Mapeo desde una fila nativa, que llega como mapa de columnas.
     */
    private RetryTask toDomainFromMap(Map<String, Object> row) {
        return new RetryTask(
                (UUID) row.get("id"),
                (UUID) row.get("tenant_id"),
                ProviderType.valueOf((String) row.get("provider")),
                (String) row.get("operation"),
                (UUID) row.get("reference_id"),
                (String) row.get("payload"),
                ((Number) row.get("attempts_count")).intValue(),
                ((Number) row.get("max_attempts")).intValue(),
                toInstant(row.get("next_retry_at")),
                (String) row.get("status"),
                (String) row.get("last_error"),
                toInstant(row.get("created_at")),
                toInstant(row.get("updated_at"))
        );
    }

    /**
     * Convierte el valor temporal de una fila nativa.
     *
     * <p>El driver de PostgreSQL devuelve las marcas de tiempo como
     * {@code java.sql.Timestamp}, no como {@code Instant}. Un casteo directo
     * fallaba con {@code ClassCastException} en la primera lectura del relay,
     * de modo que la cola de reintentos nunca llegaba a procesar nada: el
     * fallo se manifestaba como una excepcion de tipos, muy lejos de su
     * causa.
     */
    private Instant toInstant(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Instant instant) {
            return instant;
        }
        if (value instanceof Timestamp timestamp) {
            return timestamp.toInstant();
        }
        if (value instanceof OffsetDateTime offsetDateTime) {
            return offsetDateTime.toInstant();
        }
        if (value instanceof java.time.LocalDateTime localDateTime) {
            return localDateTime.toInstant(java.time.ZoneOffset.UTC);
        }
        throw new IllegalArgumentException("Valor temporal inesperado en la fila nativa: " + value.getClass());
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
