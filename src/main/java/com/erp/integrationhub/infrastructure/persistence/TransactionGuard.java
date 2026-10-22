package com.erp.integrationhub.infrastructure.persistence;

import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Comprobacion de que hay un limite transaccional abierto.
 *
 * <p>Los adaptadores de persistencia se unen a la transaccion que abre la
 * capa de aplicacion. Esta comprobacion hace que invocar uno fuera de ese
 * limite falle de forma explicita, en lugar de confirmar la escritura en una
 * transaccion implicita que romperia la atomicidad que el caso de uso
 * pretendia.
 */
@Component
public class TransactionGuard {

    public void requireActiveTransaction(String operacion) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new MissingTransactionBoundaryException(operacion);
        }
    }
}
