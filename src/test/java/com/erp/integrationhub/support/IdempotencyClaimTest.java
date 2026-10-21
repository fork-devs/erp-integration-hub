package com.erp.integrationhub.support;

import com.erp.integrationhub.domain.exception.IdempotencyConflictException;
import com.erp.integrationhub.domain.model.IdempotencyKeyState;
import com.erp.integrationhub.domain.model.IdempotencyRecord;
import com.erp.integrationhub.domain.model.IdempotencyScope;
import com.erp.integrationhub.domain.port.IdempotencyRepositoryPort;
import com.erp.integrationhub.domain.port.UnitOfWork;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * La clave de idempotencia se reclama antes de contactar al proveedor.
 *
 * <p>Reclamar es lo que serializa las peticiones con la misma clave: la
 * unicidad de (taller, alcance, clave) la impone la base de datos, de modo
 * que la segunda peticion recibe un conflicto en lugar de llamar al
 * proveedor otra vez.
 *
 * <p>Ver docs/doc-estado-implementacion.md, hallazgos P0 5.9 y P1 6.3.
 */
class IdempotencyClaimTest extends AbstractPostgresIntegrationTest {

    private static final UUID TALLER = UUID.fromString("aaaa1111-2222-3333-4444-555555555555");

    @Autowired
    private IdempotencyRepositoryPort idempotencyRepositoryPort;

    @Autowired
    private UnitOfWork unitOfWork;

    private String clave;

    @BeforeEach
    void setUp() {
        clave = "clave-" + UUID.randomUUID();
        com.erp.integrationhub.infrastructure.persistence.rls.TenantContextHolder.setTenantId(TALLER);
    }

    @org.junit.jupiter.api.AfterEach
    void tearDown() {
        com.erp.integrationhub.infrastructure.persistence.rls.TenantContextHolder.clear();
    }

    @Test
    @DisplayName("Reclamar dos veces la misma clave falla la segunda")
    void reclamarDosVecesLaMismaClaveFallaLaSegunda() {
        UUID idReclamado = unitOfWork.callInTransaction(() ->
                idempotencyRepositoryPort.claim(TALLER, IdempotencyScope.PAYMENT, clave, "huella").getId());

        assertThrows(IdempotencyConflictException.class, () ->
                unitOfWork.callInTransaction(() ->
                        idempotencyRepositoryPort.claim(TALLER, IdempotencyScope.PAYMENT, clave, "huella")),
                "La segunda reclamacion de la misma clave debe fallar");
    }

    @Test
    @DisplayName("Actualizar un registro existente no inserta una fila nueva")
    void actualizarNoInsertaUnaFilaNueva() {
        UUID idReclamado = unitOfWork.callInTransaction(() ->
                idempotencyRepositoryPort.claim(TALLER, IdempotencyScope.PAYMENT, clave, "huella").getId());

        unitOfWork.runInTransaction(() -> idempotencyRepositoryPort.updateByKey(
                TALLER, IdempotencyScope.PAYMENT, clave,
                idempotencyRepositoryPort.findByTenantAndScopeAndKey(TALLER, IdempotencyScope.PAYMENT, clave)
                        .orElseThrow()
                        .completed("{\"ok\":true}", "hash")));

        var registros = registrosDeLaClave();
        assertEquals(1, registros.size(), "Actualizar no puede insertar una fila nueva");
        assertEquals(idReclamado, registros.get(0).getId(),
                "El identificador debe conservarse: lo asigna la base de datos al insertar");
        assertEquals(IdempotencyKeyState.COMPLETED, registros.get(0).getState());
    }

    @Test
    @DisplayName("La huella de la carga util distingue la misma peticion de otra")
    void laHuellaDistingueLaMismaPeticionDeOtra() {
        String huellaPrimera = "huella-de-la-primera";
        String huellaSegunda = "huella-de-la-segunda";

        unitOfWork.runInTransaction(() ->
                idempotencyRepositoryPort.claim(TALLER, IdempotencyScope.PAYMENT, clave, huellaPrimera));

        IdempotencyRecord registro = unitOfWork.callInTransaction(() ->
                idempotencyRepositoryPort.findByTenantAndScopeAndKey(TALLER, IdempotencyScope.PAYMENT, clave)
                        .orElseThrow());

        assertTrue(registro.mismaPeticion(huellaPrimera),
                "La misma peticion debe reconocerse como tal");
        assertFalse(registro.mismaPeticion(huellaSegunda),
                "Otra peticion con la misma clave NO es la misma peticion");
    }

    @Test
    @DisplayName("La liberacion para reintento conserva el resultado anterior como no definitivo")
    void laLiberacionConservaElResultadoComoNoDefinitivo() {
        unitOfWork.runInTransaction(() ->
                idempotencyRepositoryPort.claim(TALLER, IdempotencyScope.PAYMENT, clave, "huella"));

        IdempotencyRecord conResultado = unitOfWork.callInTransaction(() ->
                idempotencyRepositoryPort.findByTenantAndScopeAndKey(TALLER, IdempotencyScope.PAYMENT, clave)
                        .orElseThrow()
                        .completed("{\"error\":\"transitorio\"}", "hash"));

        IdempotencyRecord liberado = conResultado.released();

        assertEquals(IdempotencyKeyState.RELEASED, liberado.getState());
        assertEquals("{\"error\":\"transitorio\"}", liberado.getResponsePayload(),
                "El resultado anterior se conserva");
        assertFalse(liberado.getState().resultadoDefinitivo(),
                "Un resultado liberado NO es definitivo: la peticion tiene que poder reintentarse");
    }

    @Test
    @DisplayName("Una clave completada si es un resultado definitivo")
    void unaClaveCompletadaEsDefinitiva() {
        assertTrue(IdempotencyKeyState.COMPLETED.resultadoDefinitivo());
        assertTrue(IdempotencyKeyState.REJECTED.resultadoDefinitivo());
        assertFalse(IdempotencyKeyState.IN_PROGRESS.resultadoDefinitivo());
        assertFalse(IdempotencyKeyState.RELEASED.resultadoDefinitivo());
    }

    @Test
    @DisplayName("El identificador lo asigna la base de datos")
    void elIdentificadorLoAsignaLaBaseDeDatos() {
        UUID id = unitOfWork.callInTransaction(() ->
                idempotencyRepositoryPort.claim(TALLER, IdempotencyScope.PAYMENT, clave, "huella").getId());

        assertNotEquals(null, id, "La base de datos tiene que asignar el identificador");
    }

    private java.util.List<IdempotencyRecord> registrosDeLaClave() {
        return unitOfWork.callInTransaction(() ->
                idempotencyRepositoryPort.findByTenantAndScopeAndKey(TALLER, IdempotencyScope.PAYMENT, clave)
                        .map(java.util.List::of)
                        .orElse(java.util.List.of()));
    }
}
