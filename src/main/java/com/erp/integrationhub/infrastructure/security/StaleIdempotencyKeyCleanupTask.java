package com.erp.integrationhub.infrastructure.security;

import com.erp.integrationhub.domain.port.IdempotencyRepositoryPort;
import com.erp.integrationhub.domain.port.UnitOfWork;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Liberacion de las claves de idempotencia que quedaron en curso.
 *
 * <p>Una clave en curso bloquea la operacion: la peticion siguiente
 * encuentra la clave ocupada y NO vuelve a llamar al proveedor, porque no
 * sabe si la primera llego a completarse. Si el proceso que la reclamo
 * muere a mitad, la clave se queda en curso PARA SIEMPRE, y la operacion no
 * se puede volver a intentar nunca.
 *
 * <p>La ventana de liberacion es configurable: debe ser mayor que el tiempo
 * que tarda el proveedor en responder, y menor que el tiempo que el cliente
 * considera razonable esperar un reintento.
 */
@Component
public class StaleIdempotencyKeyCleanupTask {

    private static final Logger log = LoggerFactory.getLogger(StaleIdempotencyKeyCleanupTask.class);

    private final IdempotencyRepositoryPort idempotencyRepositoryPort;
    private final UnitOfWork unitOfWork;
    private final long ventanaMinutos;

    public StaleIdempotencyKeyCleanupTask(
            IdempotencyRepositoryPort idempotencyRepositoryPort,
            UnitOfWork unitOfWork,
            @Value("${erp.integration.idempotency-stale-minutes:30}") long ventanaMinutos) {
        this.idempotencyRepositoryPort = idempotencyRepositoryPort;
        this.unitOfWork = unitOfWork;
        this.ventanaMinutos = ventanaMinutos;
    }

    @Scheduled(cron = "${erp.integration.idempotency-cleanup-cron:0 7 * * * *}")
    public void liberarClavesEnCurso() {
        Instant anterior = Instant.now().minusSeconds(ventanaMinutos * 60);
        int liberadas = unitOfWork.callInTransaction(
                () -> idempotencyRepositoryPort.releaseStaleProgress(anterior));

        if (liberadas > 0) {
            log.info("Se han liberado {} claves de idempotencia que llevaban en curso mas de {} minutos",
                    liberadas, ventanaMinutos);
        }
    }
}
