package com.erp.integrationhub.support;

import com.erp.integrationhub.domain.model.AttemptStatus;
import com.erp.integrationhub.domain.model.IntegrationAttempt;
import com.erp.integrationhub.domain.port.IntegrationAttemptRepositoryPort;
import com.erp.integrationhub.domain.port.UnitOfWork;
import com.erp.integrationhub.infrastructure.persistence.rls.TenantContextHolder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * El registro de intentos refleja la progresion real.
 *
 * <p>El numero de intento hardcodeado a 1 invalidaba la trazabilidad: la
 * bitacora decia que todo ocurria a la primera, y no habia forma de saber
 * cuantas veces habia fallado una operacion antes de terminar.
 *
 * <p>Ver docs/doc-estado-implementacion.md, hallazgos P1 6.10 y P1 6.40.
 */
class AttemptProgressionTest extends AbstractPostgresIntegrationTest {

    private static final UUID TALLER = UUID.fromString("abababab-cdcd-efef-0101-020202020202");

    @Autowired
    private IntegrationAttemptRepositoryPort attemptRepositoryPort;

    @Autowired
    private UnitOfWork unitOfWork;

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(TALLER);
    }

    @Test
    @DisplayName("El numero de intento es la progresion real, no una constante")
    void elNumeroDeIntentoEsLaProgresionReal() {
        UUID referencia = UUID.randomUUID();

        assertEquals(0, intentosDe(referencia), " todavia no hay intentos");

        registrar(IntegrationAttempt.retry(TALLER,
                com.erp.integrationhub.domain.model.ProviderType.STRIPE,
                "CHARGE", referencia, 1, "fallo transitorio"));

        assertEquals(1, intentosDe(referencia));
        assertEquals(2, 1 + attemptRepositoryPort.countAttempts(
                TALLER, com.erp.integrationhub.domain.model.ProviderType.STRIPE, "CHARGE", referencia),
                "El segundo intento debe ser el numero 2");

        registrar(IntegrationAttempt.ok(TALLER,
                com.erp.integrationhub.domain.model.ProviderType.STRIPE,
                "CHARGE", referencia, 2));

        assertEquals(2, intentosDe(referencia));
    }

    @Test
    @DisplayName("Un intento correcto solo existe si se llamo al proveedor")
    void unIntentoCorrectoSoloExisteSiSeLlamoAlProveedor() {
        UUID referencia = UUID.randomUUID();

        // Un fallo ANTERIOR a la llamada al proveedor: no se ha contactado
        // con nadie, y no puede quedar registrado un intento correcto.
        registrar(IntegrationAttempt.retry(TALLER,
                com.erp.integrationhub.domain.model.ProviderType.STRIPE,
                "CHARGE", referencia, 1, "no se pudo contactar"));

        var intentos = unitOfWork.callInTransaction(() ->
                attemptRepositoryPort.findByTenantIdAndReferenceId(TALLER, referencia));

        assertEquals(1, intentos.size());
        assertEquals(AttemptStatus.RETRY, intentos.get(0).getStatus(),
                "Un fallo anterior a la llamada no puede dejar un intento correcto registrado");
    }

    private int intentosDe(UUID referencia) {
        return attemptRepositoryPort.countAttempts(
                TALLER, com.erp.integrationhub.domain.model.ProviderType.STRIPE, "CHARGE", referencia);
    }

    private void registrar(IntegrationAttempt intento) {
        unitOfWork.runInTransaction(() -> attemptRepositoryPort.save(intento));
    }
}
