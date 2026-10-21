package com.erp.integrationhub.support;

import com.erp.integrationhub.domain.port.UnitOfWork;

import java.util.function.Supplier;

/**
 * Limite transaccional de prueba: ejecuta la operacion en linea.
 *
 * <p>La demarcacion transaccional como tal se verifica en los tests de
 * integracion, contra PostgreSQL real. Aqui lo que importa es que el caso de
 * uso delega la atomicidad en el puerto y no en una llamada suelta a un
 * repositorio.
 */
public final class DirectUnitOfWork implements UnitOfWork {

    public static final UnitOfWork INSTANCE = new DirectUnitOfWork();

    private DirectUnitOfWork() {
    }

    @Override
    public void runInTransaction(Runnable work) {
        work.run();
    }

    @Override
    public <T> T callInTransaction(Supplier<T> work) {
        return work.get();
    }
}
