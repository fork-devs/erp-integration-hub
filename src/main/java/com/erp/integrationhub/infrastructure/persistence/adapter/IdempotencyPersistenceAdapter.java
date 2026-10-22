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

        // Primero se busca la fila. Si esta LIBERADA, se puede volver a
        // reclamar: un fallo transitorio no puede dejar la clave
        // inutilizable para siempre. Si esta EN CURSO o COMPLETADA, otra
        // peticion la reclamo y no hay que volver a llamar al proveedor.
        //
        // La busqueda va PRIMERO a proposito: un INSERT que viola la
        // unicidad ABORTA la transaccion en PostgreSQL, y desde ahi la
        // transaccion no sirve para nada. Actualizando la fila liberada, la
        // transaccion sigue viva.
        Optional<IdempotencyKeyJpaEntity> existente = repository
                .findByTenantIdAndScopeAndKey(tenantId, scope.name(), key);

        if (existente.isPresent()) {
            IdempotencyKeyJpaEntity fila = existente.get();
            if (fila.getState() == null || !IdempotencyKeyState.RELEASED.name().equals(fila.getState())) {
                throw new IdempotencyConflictException(
                        "La clave de idempotencia ya fue reclamada: " + key);
            }
            fila.setState(IdempotencyKeyState.IN_PROGRESS.name());
            fila.setRequestHash(requestHash);
            fila.setResponseHash(null);
            fila.setResponsePayload(null);
            return toDomain(repository.save(fila));
        }

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
            // Dos peticiones simultaneas llegaron a la vez: una gano la
            // reclamacion, y la otra NO llama al proveedor.
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
    public int releaseStaleProgress(java.time.Instant anterior) {
        transactionGuard.requireActiveTransaction("releaseStaleIdempotencyKeys");
        return repository.releaseStaleProgress(anterior);
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
        // El identificador lo ASIGNA la base de datos, siempre: si la
        // aplicacion lo asignara, un guardado de una entidad que ya existe
        // se resolveria como una INSERCION, y la actualizacion del
        // resultado de una operacion idempotente seria imposible.
        return new IdempotencyKeyJpaEntity(
                null,
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
