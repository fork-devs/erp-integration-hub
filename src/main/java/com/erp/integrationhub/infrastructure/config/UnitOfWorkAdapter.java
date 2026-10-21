package com.erp.integrationhub.infrastructure.config;

import com.erp.integrationhub.domain.port.UnitOfWork;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.function.Supplier;

/**
 * Implementacion del limite transaccional sobre {@link TransactionTemplate}.
 *
 * <p>La plantilla usa la propagacion por defecto: si ya hay una transaccion
 * abierta, la operacion se une a ella en lugar de abrir otra. Eso es lo que
 * permite anidar demarcaciones sin crear transacciones independientes.
 */
@Component
public class UnitOfWorkAdapter implements UnitOfWork {

    private final TransactionTemplate transactionTemplate;

    public UnitOfWorkAdapter(PlatformTransactionManager transactionManager) {
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Override
    public void runInTransaction(Runnable work) {
        transactionTemplate.executeWithoutResult(status -> work.run());
    }

    @Override
    public <T> T callInTransaction(Supplier<T> work) {
        return transactionTemplate.execute(status -> work.get());
    }
}
