package com.erp.integrationhub.support;

import com.erp.integrationhub.domain.model.IdempotencyRecord;
import com.erp.integrationhub.domain.model.IdempotencyScope;
import com.erp.integrationhub.domain.port.IdempotencyRepositoryPort;
import com.erp.integrationhub.infrastructure.persistence.rls.TenantContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Aislamiento de fila en el hub: la politica de Row-Level Security impide
 * leer o escribir datos de otro taller, con independencia de los filtros que
 * lleve la consulta.
 *
 * <p>El contexto lo fija el principal autenticado, jamas la fila. Si lo
 * fijase la fila, la comprobacion de la politica seria siempre satisfacida y
 * el aislamiento no aislaria nada: por eso el test escribe una fila de un
 * taller con el contexto de otro y exige que se rechace.
 *
 * <p>Ver docs/doc-estado-implementacion.md, hallazgo P1 6.53.
 */
class TenantRlsEnforcementTest extends AbstractPostgresIntegrationTest {

    private static final UUID TALLER_PROPIO = UUID.fromString("66666666-6666-6666-6666-666666666666");
    private static final UUID TALLER_AJENO = UUID.fromString("77777777-7777-7777-7777-777777777777");

    @Autowired
    private IdempotencyRepositoryPort idempotencyRepositoryPort;

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void laPoliticaEstaActivaEnLasTresTablas() throws Exception {
        try (var connection = EmbeddedPostgresSupport.openOwnerConnection();
             var statement = connection.createStatement();
             var rs = statement.executeQuery(
                     "SELECT count(*) FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace "
                     + "WHERE n.nspname = 'public' AND c.relname IN "
                     + "('idempotency_keys','integration_attempts','integration_retry_tasks') "
                     + "AND NOT c.relforcerowsecurity")) {
            rs.next();
            assertEquals(0, rs.getInt(1), "Las tres tablas deben tener FORCE ROW LEVEL SECURITY");
        }
    }

    @Test
    void unaEscrituraDeOtroTallerEsRechazada() {
        // El contexto es el del taller propio: la fila es de otro.
        TenantContextHolder.setTenantId(TALLER_PROPIO);

        assertThrows(RuntimeException.class, () ->
                        idempotencyRepositoryPort.save(IdempotencyRecord.of(
                                TALLER_AJENO, IdempotencyScope.PAYMENT, "clave-de-otro", "{}")),
                "Escribir una fila de otro taller debe rechazarse");
    }

    @Test
    void unaLecturaDeOtroTallerNoDevuelveFilas() {
        // Se escribe una fila del taller propio con el contexto correspondiente.
        TenantContextHolder.setTenantId(TALLER_PROPIO);
        idempotencyRepositoryPort.save(IdempotencyRecord.of(
                TALLER_PROPIO, IdempotencyScope.PAYMENT, "clave-propia", "{}"));

        // Con el contexto de otro taller, la fila propia no es visible.
        TenantContextHolder.setTenantId(TALLER_AJENO);
        Optional<IdempotencyRecord> resultado =
                idempotencyRepositoryPort.findByTenantAndScopeAndKey(TALLER_PROPIO, IdempotencyScope.PAYMENT, "clave-propia");

        assertTrue(resultado.isEmpty(), "Con el contexto de otro taller no debe verse ninguna fila");
    }
}
