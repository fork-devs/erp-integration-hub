package com.erp.integrationhub.domain.port;

import java.util.function.Supplier;

/**
 * Limite transaccional de una operacion de negocio.
 *
 * <p>Es una abstraccion del dominio, sin dependencias de framework, para que
 * la capa de aplicacion pueda exigir que una operacion que escribe en varios
 * repositorios se confirme de forma conjunta sin importar como se
 * implementa la transaccion.
 *
 * <p>Sin el, cada llamada a un repositorio abre su propia transaccion: dos
 * escrituras consecutivas son dos transacciones distintas, y una operacion
 * que necesita ser atomica no lo puede ser. El registro de idempotencia y el
 * registro de intento de un cobro son el caso claro: si el primero se
 * confirma y el segundo falla, queda un cobro sin su clave de idempotencia.
 */
public interface UnitOfWork {

    void runInTransaction(Runnable work);

    <T> T callInTransaction(Supplier<T> work);
}
