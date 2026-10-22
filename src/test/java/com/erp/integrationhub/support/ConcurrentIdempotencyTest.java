package com.erp.integrationhub.support;

import com.erp.integrationhub.application.dto.command.ProcesarPagoCommand;
import com.erp.integrationhub.application.service.PaymentIntegrationService;
import com.erp.integrationhub.domain.exception.IdempotencyConflictException;
import com.erp.integrationhub.domain.model.IdempotencyKeyState;
import com.erp.integrationhub.domain.model.IdempotencyScope;
import com.erp.integrationhub.domain.model.ResultadoPago;
import com.erp.integrationhub.domain.port.IdempotencyRepositoryPort;
import com.erp.integrationhub.domain.port.PasarelaDePagoPort;
import com.erp.integrationhub.domain.port.UnitOfWork;
import com.erp.integrationhub.infrastructure.persistence.rls.TenantContextHolder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Dos peticiones simultaneas con la misma clave de idempotencia NO cobran
 * dos veces.
 *
 * <p>La reclamacion previa de la clave es lo que lo garantiza: la primera
 * peticion la reclama, y la segunda encuentra la clave ocupada y NO llama al
 * proveedor. Sin ella, las dos cobraban.
 *
 * <p>Ver docs/doc-estado-implementacion.md, hallazgos P0 5.9 y P1 6.3.
 */
class ConcurrentIdempotencyTest extends AbstractPostgresIntegrationTest {

    private static final UUID TALLER = UUID.fromString("ffff1111-2222-3333-4444-555555555555");

    @Autowired
    private PaymentIntegrationService paymentIntegrationService;

    @Autowired
    private IdempotencyRepositoryPort idempotencyRepositoryPort;

    @Autowired
    private UnitOfWork unitOfWork;

    @Autowired
    private PasarelaDePagoPort pasarelaDePagoPort;

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(TALLER);
    }

    @Test
    @DisplayName("Dos peticiones simultaneas con la misma clave llaman al proveedor una sola vez")
    void dosPeticionesSimultaneasLlamanAlProveedorUnaSolaVez() throws Exception {
        AtomicInteger llamadasAlProveedor = new AtomicInteger();
        String clave = "clave-concurrente-" + UUID.randomUUID();

        // El proveedor cuenta las llamadas: es lo que hay que comprobar.
        PasarelaDePagoPort counting = new PasarelaDePagoPort() {
            @Override
            public ResultadoPago procesarCobro(com.erp.integrationhub.domain.model.SolicitudCobro solicitud) {
                llamadasAlProveedor.incrementAndGet();
                try {
                    // Se deja pasar tiempo, de modo que las dos peticiones se
                    // solapen de verdad.
                    Thread.sleep(200);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return ResultadoPago.exitoso("ch_concurrente", "AUTH-1");
            }

            @Override
            public ResultadoPago consultarEstadoPago(String chargeId) {
                return ResultadoPago.exitoso(chargeId, "AUTH-1");
            }
        };

        PaymentIntegrationService servicio = new PaymentIntegrationService(
                counting,
                idempotencyRepositoryPort,
                attemptRepositoryPort,
                unitOfWork);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch arranque = new CountDownLatch(1);
        AtomicInteger conflictos = new AtomicInteger();

        try {
            for (int i = 0; i < 2; i++) {
                executor.submit(() -> {
                    // El contexto de taller es por hilo: cada peticion lo
                    // establece en el suyo.
                    TenantContextHolder.setTenantId(TALLER);
                    try {
                        arranque.await(5, TimeUnit.SECONDS);
                        servicio.procesarPago(new ProcesarPagoCommand(
                                TALLER,
                                UUID.randomUUID(),
                                new BigDecimal("50.00"),
                                "PEN",
                                "tok_test",
                                "Prueba concurrente",
                                "cliente@taller.pe",
                                clave));
                    } catch (IdempotencyConflictException e) {
                        conflictos.incrementAndGet();
                    } catch (Exception ignored) {
                        // El otro error posible no es lo que se comprueba aqui.
                    }
                });
            }
            arranque.countDown();
            executor.shutdown();
            assertTrue(executor.awaitTermination(30, TimeUnit.SECONDS), "Las peticiones deben terminar");
        } finally {
            executor.shutdownNow();
        }

        assertEquals(1, llamadasAlProveedor.get(),
                "Con la misma clave de idempotencia, el proveedor debe recibir UNA sola llamada: "
                        + "cobrar dos veces es perder dinero del cliente");
        assertTrue(conflictos.get() >= 1,
                "La segunda peticion debe recibir un conflicto de clave, no volver a cobrar");
    }

    @Test
    @DisplayName("Una clave liberada se puede volver a reclamar")
    void unaClaveLiberadaSePuedeVolverAReclamar() {
        String clave = "clave-liberada-" + UUID.randomUUID();

        var claimed = unitOfWork.callInTransaction(() ->
                idempotencyRepositoryPort.claim(TALLER, IdempotencyScope.PAYMENT, clave, "huella"));
        assertEquals(IdempotencyKeyState.IN_PROGRESS, claimed.getState());

        unitOfWork.runInTransaction(() -> idempotencyRepositoryPort.updateByKey(
                TALLER, IdempotencyScope.PAYMENT, clave, claimed.released()));

        var reutilizada = unitOfWork.callInTransaction(() ->
                idempotencyRepositoryPort.claim(TALLER, IdempotencyScope.PAYMENT, clave, "huella"));
        assertEquals(IdempotencyKeyState.IN_PROGRESS, reutilizada.getState(),
                "Una clave liberada tiene que poder reclamarse otra vez: si no, el reintento es imposible");
    }

    @Autowired
    private com.erp.integrationhub.domain.port.IntegrationAttemptRepositoryPort attemptRepositoryPort;
}
