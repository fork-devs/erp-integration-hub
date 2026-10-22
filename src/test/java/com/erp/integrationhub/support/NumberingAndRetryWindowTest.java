package com.erp.integrationhub.support;

import com.erp.integrationhub.domain.exception.VoucherNumberingConflictException;
import com.erp.integrationhub.domain.model.RetryTask;
import com.erp.integrationhub.domain.port.IdempotencyRepositoryPort;
import com.erp.integrationhub.domain.port.UnitOfWork;
import com.erp.integrationhub.domain.port.VoucherNumberingPort;
import com.erp.integrationhub.infrastructure.persistence.rls.TenantContextHolder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Numeracion, ventana de reintentos y liberacion de claves.
 *
 * <p>Ver docs/doc-estado-implementacion.md, hallazgos P1 6.43, P1 6.45 y P1 6.46.
 */
class NumberingAndRetryWindowTest extends AbstractPostgresIntegrationTest {

    private static final UUID TALLER = UUID.fromString("dd22dd33-dd44-dd55-dd66-dd77dd88dd99");

    @Autowired
    private VoucherNumberingPort voucherNumberingPort;

    @Autowired
    private IdempotencyRepositoryPort idempotencyRepositoryPort;

    @Autowired
    private UnitOfWork unitOfWork;

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(TALLER);
    }

    @Test
    @DisplayName("La numeracion reservada no se puede volver a reservar")
    void laNumeracionReservadaNoSePuedeVolverAReservar() {
        String serie = "F" + (int) (Math.random() * 900 + 100);
        int numero = (int) (Math.random() * 9000 + 1000);

        unitOfWork.runInTransaction(() ->
                voucherNumberingPort.reserve(TALLER, serie, numero, UUID.randomUUID()));

        assertTrue(voucherNumberingPort.isReserved(TALLER, serie, numero));

        assertThrows(VoucherNumberingConflictException.class, () ->
                unitOfWork.runInTransaction(() ->
                        voucherNumberingPort.reserve(TALLER, serie, numero, UUID.randomUUID())),
                "La numeracion de un comprobante no puede repetirse en el mismo taller");
    }

    @Test
    @DisplayName("La ventana de reintentos alcanza las 24 horas del diseno")
    void laVentanaDeReintentosAlcanzaLas24Horas() {
        long ventana = RetryTask.ventanaTotalSegundos(RetryTask.ESCALA_POR_DEFECTO);

        assertTrue(ventana >= 24 * 3600L - 600,
                "La ventana total de reintentos debe alcanzar las 24 horas, y es de " + ventana + " segundos");
    }

    @Test
    @DisplayName("Una clave de idempotencia en curso antigua se libera")
    void unaClaveEnCursoAntiguaSeLibera() {
        String clave = "clave-en-curso-" + UUID.randomUUID();

        unitOfWork.runInTransaction(() -> idempotencyRepositoryPort.claim(
                TALLER, com.erp.integrationhub.domain.model.IdempotencyScope.PAYMENT, clave, "huella"));

        // Todavia no se libera: la clave es reciente.
        int liberadas = unitOfWork.callInTransaction(
                () -> idempotencyRepositoryPort.releaseStaleProgress(Instant.now().minusSeconds(3600)));
        assertEquals(0, liberadas, "Una clave reciente no se libera");

        // Con una ventana mayor, la clave queda liberada y puede volver a
        // reclamarse.
        int liberadasAntiguas = unitOfWork.callInTransaction(
                () -> idempotencyRepositoryPort.releaseStaleProgress(Instant.now().plusSeconds(1)));
        assertTrue(liberadasAntiguas >= 1, "La clave en curso debe liberarse");
    }
}
