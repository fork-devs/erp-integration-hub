package com.erp.integrationhub.infrastructure.persistence.rls;

import com.erp.integrationhub.domain.exception.TenantContextMissingException;
import com.erp.integrationhub.infrastructure.config.PersistenceConfig;
import jakarta.persistence.EntityManager;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.UUID;

/**
 * Aplica el contexto de taller a la sesion de base de datos para que la
 * politica de Row-Level Security pueda filtrar las filas.
 *
 * <p>El valor procede SIEMPRE del principal autenticado, nunca de la fila
 * que se opera: si lo tomase de la fila, la comprobacion de la politica
 * seria siempre satisfacida y el aislamiento no aislaria nada.
 *
 * <p>El aislamiento falla cerrado: sin contexto no hay operacion, y sin
 * transaccion el SET LOCAL no tendria efecto.
 */
@Aspect
@Component
@Order(PersistenceConfig.TENANT_RLS_ASPECT_ORDER)
public class PostgresTenantRlsAspect {

    private final EntityManager entityManager;

    public PostgresTenantRlsAspect(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Before("execution(* com.erp.integrationhub.infrastructure.persistence.adapter..*(..))")
    public void applyTenantContext() {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new TenantContextMissingException(
                    "La operacion de persistencia debe ejecutarse dentro de una transaccion: "
                    + "fuera de ella no puede garantizarse el aislamiento por taller.");
        }

        UUID tenantId = TenantContextHolder.getTenantId();
        if (tenantId == null) {
            throw new TenantContextMissingException(
                    "La operacion requiere un contexto de taller establecido y no lo tiene.");
        }

        // El valor se interpola porque SET LOCAL no admite parametros
        // preparados. Es un UUID, de modo que no introduce una via de inyeccion.
        entityManager.createNativeQuery("SET LOCAL app.tenant_id = '" + tenantId + "'").executeUpdate();
    }
}
