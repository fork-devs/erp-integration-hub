package com.erp.integrationhub.domain.model;

import java.io.Serializable;

/**
 * Estado de una clave de idempotencia.
 *
 * <p>Sin estado, un fallo transitorio dejaba la clave con la respuesta del
 * fallo, y la siguiente peticion con la misma clave recibia ese fallo como
 * resultado definitivo: la operacion se perdia, y el usuario no tenia forma
 * de reintentarla.
 */
public enum IdempotencyKeyState implements Serializable {

    /**
     * La operacion se esta ejecutando. La clave esta RECLAMADA: si otra
     * peticion llega con la misma clave, no debe contactar al proveedor otra
     * vez.
     */
    IN_PROGRESS,

    /**
     * La operacion termino con exito, y el resultado almacenado es
     * definitivo.
     */
    COMPLETED,

    /**
     * La operacion fallo de forma transitoria. La clave se libera para que
     * un reintento pueda volver a intentarlo, y el resultado almacenado NO
     * es definitivo.
     */
    RELEASED,

    /**
     * La operacion fallo de forma definitiva. El resultado almacenado es
     * definitivo, y una peticion con la misma clave recibira ese rechazo.
     */
    REJECTED;

    /**
     * Indica si el resultado almacenado es definitivo, es decir, si una
     * peticion con la misma clave debe devolverlo tal cual.
     */
    public boolean resultadoDefinitivo() {
        return this == COMPLETED || this == REJECTED;
    }
}
