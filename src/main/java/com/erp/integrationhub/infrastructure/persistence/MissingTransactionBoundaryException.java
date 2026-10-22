package com.erp.integrationhub.infrastructure.persistence;

/**
 * Excepcion de infraestructura: una operacion de persistencia se ha invocado
 * sin un limite transaccional.
 *
 * <p>Los adaptadores de persistencia no abren su propia transaccion: se unen
 * a la que abre la capa de aplicacion. Si se invocan sin ella, cada
 * escritura se confirmaria por separado y una operacion que necesita ser
 * atomica no lo seria. Por eso se rechaza de forma explicita en lugar de
 * confirmarse en silencio.
 *
 * <p>Ver docs/doc-estado-implementacion.md, hallazgos P0 5.3 y P0 5.8.
 */
public class MissingTransactionBoundaryException extends RuntimeException {

    public MissingTransactionBoundaryException(String operacion) {
        super("La operacion de persistencia '" + operacion
                + "' requiere un limite transaccional abierto por la capa de aplicacion. "
                + "Los adaptadores se unen a la transaccion en curso; no abren una propia.");
    }
}
