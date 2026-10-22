package com.erp.integrationhub.support;

import com.erp.integrationhub.application.service.RetryTaskReactivationService;
import com.erp.integrationhub.domain.model.ProviderType;
import com.erp.integrationhub.domain.model.RetryTask;
import com.erp.integrationhub.domain.port.RetryQueueRepositoryPort;
import com.erp.integrationhub.domain.port.UnitOfWork;
import com.erp.integrationhub.infrastructure.persistence.rls.TenantContextHolder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * La reactivacion de una tarea en cola de muertos.
 *
 * <p>Una tarea agotada ya no se reintenta sola: su operacion se dio por
 * perdida. La reactivacion la devuelve a la cola de pendientes con el
 * contador restablecido, de modo que un operador pueda reintentarla cuando la
 * causa del fallo se haya resuelto.
 */
class RetryTaskReactivationTest extends AbstractPostgresIntegrationTest {

    private static final UUID TALLER = UUID.fromString("ee33ee44-ee55-ee66-ee77-ee88ee99ee00");

    @Autowired
    private RetryQueueRepositoryPort retryQueueRepositoryPort;

    @Autowired
    private UnitOfWork unitOfWork;

    private RetryTaskReactivationService servicio;

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(TALLER);
        servicio = new RetryTaskReactivationService(retryQueueRepositoryPort, unitOfWork);
    }

    @Test
    @DisplayName("La reactivacion devuelve la tarea a pendiente y restablece el contador")
    void laReactivacionDevuelveLaTareaAPendiente() {
        RetryTask task = RetryTask.create(
                TALLER, ProviderType.APISUNAT, "EMIT_VOUCHER", UUID.randomUUID(), "{}", 5);

        // Se agota: la tarea pasa a la cola de muertos.
        for (int i = 0; i < 5; i++) {
            task.recordFailure("fallo");
        }
        assertEquals("DLQ", task.getStatus(), "La tarea agotada esta en la cola de muertos");
        unitOfWork.runInTransaction(() -> retryQueueRepositoryPort.save(task));

        RetryTask reactivada = servicio.reactivate(task.getId());

        assertEquals("PENDING", reactivada.getStatus(),
                "Una tarea reactivada vuelve a la cola de pendientes");
        assertEquals(0, reactivada.getAttemptsCount(),
                "La reactivacion restablece el contador de intentos");
        assertTrue(reactivada.getNextRetryAt() != null, "La tarea reactivada se reprograma");
    }
}
