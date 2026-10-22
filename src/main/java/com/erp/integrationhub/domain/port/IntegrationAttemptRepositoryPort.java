package com.erp.integrationhub.domain.port;

import com.erp.integrationhub.domain.model.AttemptStatus;
import com.erp.integrationhub.domain.model.IntegrationAttempt;

import java.util.List;
import java.util.UUID;

public interface IntegrationAttemptRepositoryPort {
    IntegrationAttempt save(IntegrationAttempt attempt);

    /**
     * Numero de intentos ya registrados para una operacion.
     *
     * <p>El numero de intento HARDCODEADO a 1 invalida la trazabilidad: la
     * bitacora dice que todo ocurrio a la primera, y no hay forma de saber
     * cuantas veces fallo una operacion antes de terminar.
     */
    int countAttempts(UUID tenantId, com.erp.integrationhub.domain.model.ProviderType provider,
                      String operation, UUID referenceId);
    List<IntegrationAttempt> findByTenantId(UUID tenantId, int limit);
    List<IntegrationAttempt> findByTenantIdAndReferenceId(UUID tenantId, UUID referenceId);
    List<IntegrationAttempt> findByTenantIdAndStatus(UUID tenantId, AttemptStatus status, int limit);
}
