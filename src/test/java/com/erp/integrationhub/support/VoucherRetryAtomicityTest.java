package com.erp.integrationhub.support;

import com.erp.integrationhub.application.service.RetrySchedulerService;
import com.erp.integrationhub.domain.model.ProviderType;
import com.erp.integrationhub.domain.model.RetryTask;
import com.erp.integrationhub.domain.port.EmisorTributarioPort;
import com.erp.integrationhub.domain.port.IdempotencyRepositoryPort;
import com.erp.integrationhub.domain.port.IntegrationAttemptRepositoryPort;
import com.erp.integrationhub.domain.port.PasarelaDePagoPort;
import com.erp.integrationhub.domain.port.RetryQueueRepositoryPort;
import com.erp.integrationhub.domain.port.UnitOfWork;
import com.erp.integrationhub.infrastructure.persistence.entity.IdempotencyKeyJpaEntity;
import com.erp.integrationhub.infrastructure.persistence.repository.SpringDataIdempotencyRepository;
import com.erp.integrationhub.infrastructure.persistence.rls.TenantContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

/**
 * Un reintento que ya fue aceptado por el proveedor no se reenvia.
 *
 * <p>Si el proveedor emito el comprobante y el registro local falla, la
 * tarea no puede volver a la cola de pendientes: reenviarla emitiria un
 * comprobante duplicado, que es un problema mucho mayor que un registro
 * local que falta.
 *
 * <p>Ver docs/doc-estado-implementacion.md, hallazgo P0 5.3.
 */
class VoucherRetryAtomicityTest extends AbstractPostgresIntegrationTest {

    private static final UUID TALLER = UUID.fromString("dddd4444-5555-6666-7777-888899990000");
    private static final UUID COMPROBANTE = UUID.fromString("eeee5555-6666-7777-8888-999900001111");

    @Autowired
    private RetrySchedulerService retrySchedulerService;

    @Autowired
    private RetryQueueRepositoryPort retryQueueRepositoryPort;

    @Autowired
    private IdempotencyRepositoryPort idempotencyRepositoryPort;

    @Autowired
    private IntegrationAttemptRepositoryPort attemptRepositoryPort;

    @Autowired
    private UnitOfWork unitOfWork;

    @Autowired
    private EmisorTributarioPort emisorTributarioPort;

    @Autowired
    private PasarelaDePagoPort pasarelaDePagoPort;

    @MockitoSpyBean
    private SpringDataIdempotencyRepository springDataIdempotencyRepository;

    /**
     * El emisor tributario real llama a la API de SUNAT. En el test se
     * sustituye por uno que responde con exito: lo que se verifica es el
     * comportamiento del sistema ante un resultado ACCEPTED, no la
     * comunicacion con el proveedor.
     */
    @MockitoBean
    private EmisorTributarioPort emisorTributarioPortStub;

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(TALLER);
        when(emisorTributarioPortStub.emitirComprobante(any()))
                .thenReturn(com.erp.integrationhub.domain.model.ResultadoDeclaracion.aceptado(
                        "0", "La factura numero F001-1, ha sido aceptada", null, null, null, null));
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void unFalloAlEscribirLaIdempotenciaNoReprogramaElComprobante() {
        RetryTask tarea = tareaPendiente();

        doThrow(new RuntimeException("no se puede escribir la idempotencia"))
                .when(springDataIdempotencyRepository).save(any(IdempotencyKeyJpaEntity.class));

        retrySchedulerService.processPendingRetries(10);

        RetryTask despues = retryQueueRepositoryPort.findById(tarea.getId()).orElseThrow();
        assertNotEquals("PENDING", despues.getStatus(),
                "La tarea no debe volver a la cola de pendientes: el comprobante ya fue emitido");
        assertEquals("COMPLETED", despues.getStatus(),
                "El comprobante ya fue emitido por el proveedor: la tarea no puede reprogramarse para "
                        + "reenviarlo, porque eso.emitiria un comprobante duplicado. Un fallo de la cache "
                        + "de idempotencia no cambia el resultado de la emision.");
        assertEquals(0, idempotencyRepositoryPort
                .findByTenantAndScopeAndKey(TALLER,
                        com.erp.integrationhub.domain.model.IdempotencyScope.VOUCHER,
                        COMPROBANTE.toString())
                .map(java.util.List::of)
                .orElse(java.util.List.of())
                .size(),
                "El registro de idempotencia no pudo escribirse, y eso es lo que se verifica");
    }

    @Test
    void sinFalloLaTareaQuedaCompletada() {
        RetryTask tarea = tareaPendiente();

        retrySchedulerService.processPendingRetries(10);

        RetryTask despues = retryQueueRepositoryPort.findById(tarea.getId()).orElseThrow();
        assertEquals("COMPLETED", despues.getStatus(),
                "Con un resultado exitoso del proveedor, la tarea debe quedar completada");
    }

    private RetryTask tareaPendiente() {
        String payload = """
                {
                  "tenantId": "%s",
                  "comprobanteId": "%s",
                  "tipo": "FACTURA",
                  "serie": "F001",
                  "numero": 1,
                  "clienteTipoDoc": "DNI",
                  "clienteNumDoc": "12345678",
                  "clienteDenominacion": "Cliente de prueba",
                  "clienteDireccion": "Av. Siempre Viva 742",
                  "clienteEmail": "cliente@taller.pe",
                  "moneda": "PEN",
                  "totalGravada": 100.00,
                  "totalIgv": 18.00,
                  "total": 118.00,
                  "items": [
                    {
                      "unidadMedida": "NIU",
                      "descripcion": "Servicio de prueba",
                      "cantidad": 1,
                      "valorUnitario": 100.00,
                      "precioUnitario": 118.00,
                      "igv": 18.00,
                      "total": 118.00
                    }
                  ],
                  "fechaEmision": "2026-01-15",
                  "idempotencyKey": "%s"
                }
                """.formatted(TALLER, COMPROBANTE, COMPROBANTE);

        // La tarea se crea con la proxima ejecucion ya vencida: el planificador
        // solo recoge las tareas cuya hora de reintento ha pasado.
        java.time.Instant ahora = java.time.Instant.now();
        RetryTask tarea = new RetryTask(
                UUID.randomUUID(),
                TALLER,
                ProviderType.APISUNAT,
                "EMIT_VOUCHER",
                COMPROBANTE,
                payload,
                0,
                5,
                ahora.minusSeconds(60),
                "PENDING",
                null,
                ahora,
                ahora);

        unitOfWork.runInTransaction(() -> retryQueueRepositoryPort.save(tarea));
        return tarea;
    }
}
