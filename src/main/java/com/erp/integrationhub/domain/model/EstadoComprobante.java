package com.erp.integrationhub.domain.model;

import java.io.Serializable;

/**
 * Estado de un comprobante ante la autoridad fiscal.
 *
 * <p>El estado era TEXTO LIBRE, y de ahi venian dos problemas: un estado
 * desconocido pasaba por un {@code equalsIgnoreCase} y se traducía a
 * "rechazado" o a "aceptado" segun el caso, y el estado real del proveedor
 * se perdia.
 *
 * <p>{@code OBSERVADO} es un estado DISTINTO de {@code RECHAZADO}: un
 * comprobante observado ha sido ACEPTADO con una observacion, y no se puede
 * volver a emitir. Tratarlo como rechazado hacia que el taller creyera que
 * no habia emitted nada, y que el comprobante se perdia.
 */
public enum EstadoComprobante implements Serializable {

    /** Aceptado por la autoridad fiscal. */
    ACEPTADO,

    /**
     * Aceptado CON OBSERVACION. El comprobante existe y no se puede volver
     * a emitir.
     */
    OBSERVADO,

    /** Rechazado de forma definitiva: no se ha emitido. */
    RECHAZADO,

    /** Enviado, a la espera de la respuesta de la autoridad fiscal. */
    PENDIENTE,

    /** Anulado. */
    ANULADO,

    /** Emitido, pero con un estado que el hub no conoce. */
    DESCONOCIDO;

    /**
     * Estado que la API ya expone para un comprobante en proceso. Se
     * mantiene por compatibilidad con lo publicado.
     */
    public static final String PENDIENTE_SUNAT_ALIAS = "PENDIENTE_SUNAT";

    /**
     * Traduce el estado que informa el proveedor. Un estado desconocido NO se
     * convierte en un rechazo: se reconoce como desconocido, que es la
     * informacion honesta.
     */
    public static EstadoComprobante desdeProveedor(String estado) {
        if (estado == null || estado.isBlank()) {
            return DESCONOCIDO;
        }
        String normalizado = estado.trim().toUpperCase();
        return switch (normalizado) {
            case "ACEPTADO", "ACEPTADA", "ACEPT" -> ACEPTADO;
            case "OBSERVADO", "OBSERVADA", "CON OBSERVACIONES" -> OBSERVADO;
            case "RECHAZADO", "RECHAZADA", "ERROR" -> RECHAZADO;
            case "PENDIENTE", "PENDIENTE_SUNAT", "EN PROCESO" -> PENDIENTE;
            case "ANULADO", "ANULADA" -> ANULADO;
            default -> DESCONOCIDO;
        };
    }

    /**
     * Indica si el comprobante ha sido emitido, con o sin observacion.
     */
    public boolean emitido() {
        return this == ACEPTADO || this == OBSERVADO;
    }

    /**
     * Indica si el estado permite reintentar la operacion. Un rechazo
     * definitivo no; una observacion, tampoco: el comprobante ya existe.
     */
    public boolean reintentable() {
        return this == PENDIENTE || this == DESCONOCIDO;
    }
}
