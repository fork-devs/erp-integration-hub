package com.erp.integrationhub.infrastructure.persistence.adapter;

import com.erp.integrationhub.domain.exception.IdempotencyConflictException;
import com.erp.integrationhub.domain.model.IdempotencyKeyState;
import com.erp.integrationhub.domain.model.IdempotencyRecord;
import com.erp.integrationhub.domain.model.IdempotencyScope;
import com.erp.integrationhub.domain.port.IdempotencyRepositoryPort;
import com.erp.integrationhub.infrastructure.persistence.entity.IdempotencyKeyJpaEntity;
import com.erp.integrationhub.infrastructure.persistence.repository.SpringDataIdempotencyRepository;
import com.erp.integrationhub.infrastructure.persistence.TransactionGuard;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/**
 * Registro de claves de idempotencia.
 *
 * <p>La operacion de RECLAMAR es lo que serializa las peticiones con la misma clave:
 * la fila se inserta con el identificador ASIGNADO por la base de datos, y
 * la unicidad de (taller, alcance, clave) hace que la segunda peticion
 * recibe un error de duplicado en lugar de llamar al proveedor otra vez.
 *
 * <p>La ACTUALIZACION por clave de negocio no inserta una fila nueva: el
 * identificador es el de la fila reclamada, de modo que el historial de
 * intentos no se multiplica.
 */
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
    @Transactional(readOnly = true)
    public Optional<IdempotencyRecord> findByTenantAndScopeAndKey(UUID tenantId, IdempotencyScope scope, String key) {
        return repository.findByTenantIdAndScopeAndKey(tenantId, scope.name(), key).map(this::toDomain);
    }

    @Override
    public IdempotencyRecord claim(UUID tenantId, IdempotencyScope scope, String key, String requestHash) {
        transactionGuard.requireActiveTransaction("claimIdempotencyKey");
        try {
            IdempotencyKeyJpaEntity entity = new IdempotencyKeyJpaEntity(
                    null,
                    tenantId,
                    scope.name(),
                    key,
                    requestHash,
                    IdempotencyKeyState.IN_PROGRESS.name(),
                    null,
                    null,
                    java.time.Instant.now());
            IdempotencyKeyJpaEntity saved = repository.save(entity);
            // El INSERT se hace YA: sin el, Hibernate lo aplaza al commit de
            // la transaccion, y el conflicto de clave se detectaria fuera de
            // este metodo, sin poder convertirlo en su propio error de
            // dominio.
            repository.flush();
            return toDomain(saved);
        } catch (DataIntegrityViolationException e) {
            // La clave ya existe: otra peticion la reclamo antes. La unicidad
            // de la base de datos es lo que serializa las dos.
            throw new IdempotencyConflictException(
                    "La clave de idempotencia ya fue reclamada: " + key);
        }
    }

    @Override
    public IdempotencyRecord updateByKey(UUID tenantId, IdempotencyScope scope, String key,
                                          IdempotencyRecord record) {
        transactionGuard.requireActiveTransaction("updateIdempotencyRecordByKey");

        IdempotencyKeyJpaEntity entity = repository
                .findByTenantIdAndScopeAndKey(tenantId, scope.name(), key)
                .orElseThrow(() -> new IllegalStateException(
                        "La clave de idempotencia " + key + " no existe y no se puede actualizar"));

        entity.setRequestHash(record.getRequestHash());
        entity.setState(record.getState().name());
        entity.setResponseHash(record.getResponseHash());
        entity.setResponsePayload(record.getResponsePayload());

        return toDomain(repository.save(entity));
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
                entity.getRequestHash(),
                entity.getResponseHash(),
                entity.getResponsePayload(),
                entity.getState() != null
                        ? IdempotencyKeyState.valueOf(entity.getState())
                        : IdempotencyKeyState.COMPLETED,
                entity.getCreatedAt()
        );
    }

    private IdempotencyKeyJpaEntity toEntity(IdempotencyRecord domain) {
        return new IdempotencyKeyJpaEntity(
                domain.getId(),
                domain.getTenantId(),
                domain.getScope().name(),
                domain.getKey(),
                domain.getRequestHash(),
                domain.getState().name(),
                domain.getResponseHash(),
                domain.getResponsePayload(),
                domain.getCreatedAt()
        );
    }
}
