package com.erp.integrationhub.domain.port;

import com.erp.integrationhub.domain.model.IdempotencyRecord;
import com.erp.integrationhub.domain.model.IdempotencyScope;

import java.util.Optional;
import java.util.UUID;

/**
 * Registro de claves de idempotencia.
 *
 * <p>La operacion tiene TRES pasos, y el orden importa: <strong>reclamar</strong>
 * la clave, <strong>contactar</strong> al proveedor, y <strong>actualizar</strong>
 * el registro con el resultado.
 *
 * <p>Reclamar antes de contactar es lo que evita el cobro doble: si dos
 * peticiones llegan con la misma clave, la segunda encuentra la clave
 * reclamada y NO vuelve a llamar al proveedor.
 */
public interface IdempotencyRepositoryPort {

    Optional<IdempotencyRecord> findByTenantAndScopeAndKey(UUID tenantId, IdempotencyScope scope, String key);

    /**
     * Reclama la clave: la inserta en estado EN CURSO y falla si ya existe.
     *
     * <p>Es la operacion que serializa las peticiones con la misma clave. La
     * unicidad de (taller, alcance, clave) la impone la base de datos, de
     * modo que dos peticiones simultaneas no puedan reclamar la misma clave:
     * una gana, y la otra recibe el error de duplicado.
     */
    IdempotencyRecord claim(UUID tenantId, IdempotencyScope scope, String key, String requestHash);

    /**
     * Actualiza el registro existente por su clave de negocio —taller,
     * alcance y clave—, sin insertar una fila nueva.
     */
    IdempotencyRecord updateByKey(UUID tenantId, IdempotencyScope scope, String key, IdempotencyRecord record);

    IdempotencyRecord save(IdempotencyRecord record);
}
