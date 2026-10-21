package com.erp.integrationhub.domain.model;

import java.io.Serializable;

/**
 * Resultado de una declaracion ante la autoridad fiscal.
 *
 * <p>El estado es un {@link EstadoComprobante}, no texto libre: un estado
 * desconocido ya no se confunde con un rechazo.
 *
 * <p>El codigo de respuesta es el que INFORMO el proveedor. Antes se
 * sustituia por un literal, de modo que el diagnostico que llegaba al taller
 * no era el de SUNAT.
 */
public record ResultadoDeclaracion(
        boolean exitoso,
        EstadoComprobante estado,
        String codigoRespuestaSunat,
        String mensajeSunat,
        String pdfUrl,
        String xmlUrl,
        String cdrUrl,
        String hashCdr
) implements Serializable {

    public static ResultadoDeclaracion aceptado(String codigo, String mensaje, String pdfUrl, String xmlUrl,
                                                 String cdrUrl, String hashCdr) {
        return new ResultadoDeclaracion(true, EstadoComprobante.ACEPTADO, codigo, mensaje, pdfUrl, xmlUrl, cdrUrl, hashCdr);
    }

    /**
     * Aceptado CON OBSERVACION. El comprobante existe y no se puede volver
     * a emitir: tratarlo como rechazo hacia que el taller creyera que no se
     * habia emitido nada.
     */
    public static ResultadoDeclaracion observado(String codigo, String mensaje, String pdfUrl, String xmlUrl,
                                                 String cdrUrl, String hashCdr) {
        return new ResultadoDeclaracion(true, EstadoComprobante.OBSERVADO, codigo, mensaje, pdfUrl, xmlUrl, cdrUrl, hashCdr);
    }

    public static ResultadoDeclaracion pendiente(String mensaje) {
        return new ResultadoDeclaracion(true, EstadoComprobante.PENDIENTE, "0", mensaje, null, null, null, null);
    }

    public static ResultadoDeclaracion rechazado(String codigo, String mensaje) {
        return new ResultadoDeclaracion(false, EstadoComprobante.RECHAZADO, codigo, mensaje, null, null, null, null);
    }

    public static ResultadoDeclaracion anulado(String codigo, String mensaje, String hashTicket) {
        return new ResultadoDeclaracion(true, EstadoComprobante.ANULADO, codigo, mensaje, null, null, null, hashTicket);
    }

    /**
     * Traduce el estado que informa el proveedor, conservando su codigo de
     * respuesta.
     */
    public static ResultadoDeclaracion conEstado(EstadoComprobante estado, String codigo, String mensaje,
                                                  String pdfUrl, String xmlUrl, String cdrUrl, String hashCdr) {
        EstadoComprobante real = estado != null ? estado : EstadoComprobante.DESCONOCIDO;
        return new ResultadoDeclaracion(real.emitido(), real, codigo, mensaje, pdfUrl, xmlUrl, cdrUrl, hashCdr);
    }

    /**
     * El estado, en el texto que consume la API. Se mantiene el metodo para
     * no cambiar el contrato de la respuesta.
     */
    public String estadoSunat() {
        if (estado == null) {
            return EstadoComprobante.DESCONOCIDO.name();
        }
        // La API ya exponia "PENDIENTE_SUNAT" para un comprobante en proceso,
        // y los consumidores de la respuesta lo tienen en cuenta. Se mantiene
        // ese texto, y el estado del dominio es el que manda en la logica.
        return estado == EstadoComprobante.PENDIENTE
                ? EstadoComprobante.PENDIENTE_SUNAT_ALIAS
                : estado.name();
    }
}
