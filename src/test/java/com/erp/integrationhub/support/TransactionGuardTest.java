package com.erp.integrationhub.support;

import com.erp.integrationhub.infrastructure.persistence.MissingTransactionBoundaryException;
import com.erp.integrationhub.infrastructure.persistence.TransactionGuard;
import com.erp.integrationhub.infrastructure.persistence.adapter.IdempotencyPersistenceAdapter;
import com.erp.integrationhub.infrastructure.persistence.adapter.IntegrationAttemptPersistenceAdapter;
import com.erp.integrationhub.infrastructure.persistence.adapter.RetryQueuePersistenceAdapter;
import com.erp.integrationhub.infrastructure.persistence.repository.SpringDataIdempotencyRepository;
import com.erp.integrationhub.infrastructure.persistence.repository.SpringDataIntegrationAttemptRepository;
import com.erp.integrationhub.infrastructure.persistence.repository.SpringDataRetryTaskRepository;
import com.erp.integrationhub.infrastructure.persistence.rls.PlatformScopeQueryExecutor;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * Los adaptadores de escritura del hub se unen a la transaccion en curso: no
 * abren una propia.
 *
 * <p>Invocarlos fuera de un limite transaccional falla de forma explicita,
 * en lugar de confirmar la escritura en una transaccion implicita que
 * romperia la atomicidad que el caso de uso pretendia.
 *
 * <p>Ver docs/doc-estado-implementacion.md, hallazgos P0 5.3 y P0 5.8.
 */
class TransactionGuardTest {

    @Test
    void elAdaptadorDeIdempotenciaFallaFueraDeUnLimiteTransaccional() {
        IdempotencyPersistenceAdapter adapter = new IdempotencyPersistenceAdapter(
                mock(SpringDataIdempotencyRepository.class),
                new TransactionGuard());

        MissingTransactionBoundaryException excepcion = assertThrows(
                MissingTransactionBoundaryException.class,
                () -> adapter.save(null));

        assertTrue(excepcion.getMessage().contains("saveIdempotencyRecord"));
    }

    @Test
    void elAdaptadorDeIntentosFallaFueraDeUnLimiteTransaccional() {
        IntegrationAttemptPersistenceAdapter adapter = new IntegrationAttemptPersistenceAdapter(
                mock(SpringDataIntegrationAttemptRepository.class),
                mock(com.erp.integrationhub.infrastructure.persistence.rls.PlatformScopeQueryExecutor.class),
                new TransactionGuard());

        MissingTransactionBoundaryException excepcion = assertThrows(
                MissingTransactionBoundaryException.class,
                () -> adapter.save(null));

        assertTrue(excepcion.getMessage().contains("saveIntegrationAttempt"));
    }

    @Test
    void elAdaptadorDeLaColaDeReintentosFallaFueraDeUnLimiteTransaccional() {
        RetryQueuePersistenceAdapter adapter = new RetryQueuePersistenceAdapter(
                mock(SpringDataRetryTaskRepository.class),
                mock(PlatformScopeQueryExecutor.class),
                new TransactionGuard());

        MissingTransactionBoundaryException excepcion = assertThrows(
                MissingTransactionBoundaryException.class,
                () -> adapter.save(null));

        assertTrue(excepcion.getMessage().contains("saveRetryTask"));
    }

    @Test
    void elMensajeDistingueLaAusenciaDeDemarcacionDeUnFalloDeConsulta() {
        // Es lo que lleva el diagnostico en produccion a la hipotesis
        // correcta: un fallo de la consulta y una falta de limite
        // transaccional son cosas distintas.
        MissingTransactionBoundaryException excepcion = assertThrows(
                MissingTransactionBoundaryException.class,
                () -> new TransactionGuard().requireActiveTransaction("operacionDePrueba"));

        assertTrue(excepcion.getMessage().contains("limite transaccional"));
    }
}
