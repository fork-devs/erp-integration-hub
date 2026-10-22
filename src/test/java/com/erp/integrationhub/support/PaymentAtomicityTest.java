package com.erp.integrationhub.support;

import com.erp.integrationhub.application.dto.command.ProcesarPagoCommand;
import com.erp.integrationhub.application.service.PaymentIntegrationService;
import com.erp.integrationhub.domain.model.IdempotencyScope;
import com.erp.integrationhub.domain.port.IdempotencyRepositoryPort;
import com.erp.integrationhub.domain.port.IntegrationAttemptRepositoryPort;
import com.erp.integrationhub.domain.port.PasarelaDePagoPort;
import com.erp.integrationhub.domain.port.UnitOfWork;
import com.erp.integrationhub.infrastructure.persistence.entity.IdempotencyKeyJpaEntity;
import com.erp.integrationhub.infrastructure.persistence.repository.SpringDataIdempotencyRepository;
import com.erp.integrationhub.infrastructure.persistence.rls.TenantContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

/**
 * El registro del intento de cobro y el registro de idempotencia se confirman
 * juntos.
 *
 * <p>Si la escritura de la idempotencia falla despues de haber registrado el
 * intento, queda un cobro sin su clave de idempotencia: la siguiente peticion
 * con la misma clave no encontraria el registro y cobraria de nuevo.
 *
 * <p>Ver docs/doc-estado-implementacion.md, hallazgo P0 5.3.
 */
class PaymentAtomicityTest extends AbstractPostgresIntegrationTest {

    private static final UUID TALLER = UUID.fromString("bbbb2222-4444-5555-6666-777788889999");

    // Cada test usa su propio pago y su propia clave: la idempotencia
    // devolveria el resultado cacheado de la peticion anterior y el test
    // no llegaria a escribir nada.
    private UUID pago;
    private String clave;

    @Autowired
    private PaymentIntegrationService paymentIntegrationService;

    @Autowired
    private IdempotencyRepositoryPort idempotencyRepositoryPort;

    @Autowired
    private IntegrationAttemptRepositoryPort attemptRepositoryPort;

    @Autowired
    private UnitOfWork unitOfWork;

    @Autowired
    private PasarelaDePagoPort pasarelaDePagoPort;

    @MockitoSpyBean
    private SpringDataIdempotencyRepository springDataIdempotencyRepository;

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(TALLER);
        pago = UUID.randomUUID();
        clave = "clave-" + UUID.randomUUID();
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void unFalloAlEscribirLaIdempotenciaNoDejaUnIntentoDeCobroRegistrado() {
        doThrow(new RuntimeException("no se puede escribir la idempotencia"))
                .when(springDataIdempotencyRepository).save(any(IdempotencyKeyJpaEntity.class));

        org.junit.jupiter.api.Assertions.assertThrows(RuntimeException.class, () -> cobrar());

        assertEquals(0, intentosRegistrados(),
                "No debe quedar un intento de cobro registrado sin su clave de idempotencia");
        assertTrue(registroDeIdempotencia().isEmpty(),
                "No debe quedar un registro de idempotencia sin su intento de cobro");
    }

    @Test
    void sinFalloSeConfirmanLasDosEscriturasJuntas() {
        cobrar();

        assertEquals(1, intentosRegistrados(), "El intento de cobro debe quedar registrado");
        assertEquals(1, registroDeIdempotencia().size(), "El registro de idempotencia debe quedar escrito");
    }

    private void cobrar() {
        paymentIntegrationService.procesarPago(new ProcesarPagoCommand(
                TALLER,
                pago,
                new BigDecimal("50.00"),
                "PEN",
                "tok_test_123",
                "Compra de prueba",
                "cliente@taller.pe",
                clave));
    }

    private long intentosRegistrados() {
        return attemptRepositoryPort.findByTenantIdAndReferenceId(TALLER, pago).size();
    }

    private java.util.List<?> registroDeIdempotencia() {
        return idempotencyRepositoryPort
                .findByTenantAndScopeAndKey(TALLER, IdempotencyScope.PAYMENT, clave)
                .map(java.util.List::of)
                .orElse(java.util.List.of());
    }
}
