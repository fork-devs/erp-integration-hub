package com.erp.integrationhub.domain.usecase;

import com.erp.integrationhub.domain.model.RetryTask;

import java.util.UUID;

/**
 * Reactivacion de una tarea en cola de muertos.
 *
 * <p>Una tarea en cola de muertos ya no se reintenta sola: su operacion se
 * dio por perdida. La reactivacion la devuelve a la cola de pendientes con
 * el contador de intentos restablecido, de modo que un operador puede
 * reintentarla una vez que la causa del fallo se ha resuelto.
 */
public interface ReactivateRetryTaskUseCase {

    /**
     * @return la tarea reactivada
     * @throws com.erp.integrationhub.domain.exception.RetryTaskNotFoundException
     *         si la tarea no existe
     */
    RetryTask reactivate(UUID taskId);
}
