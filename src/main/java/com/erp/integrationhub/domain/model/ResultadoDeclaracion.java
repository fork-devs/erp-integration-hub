package com.erp.integrationhub.domain.model;

import java.io.Serializable;

public record ResultadoDeclaracion(
        boolean exitoso,
        String estadoSunat, // ACEPTADO, OBSERVADO, RECHAZADO, PENDIENTE_SUNAT
        String codigoRespuestaSunat,
        String mensajeSunat,
        String pdfUrl,
        String xmlUrl,
        String cdrUrl,
        String hashCdr
) implements Serializable {
    public static ResultadoDeclaracion aceptado(String codigo, String mensaje, String pdfUrl, String xmlUrl, String cdrUrl, String hashCdr) {
        return new ResultadoDeclaracion(true, "ACEPTADO", codigo, mensaje, pdfUrl, xmlUrl, cdrUrl, hashCdr);
    }

    public static ResultadoDeclaracion pendiente(String mensaje) {
        return new ResultadoDeclaracion(true, "PENDIENTE_SUNAT", "0", mensaje, null, null, null, null);
    }

    public static ResultadoDeclaracion rechazado(String codigo, String mensaje) {
        return new ResultadoDeclaracion(false, "RECHAZADO", codigo, mensaje, null, null, null, null);
    }

    public static ResultadoDeclaracion anulado(String codigo, String mensaje, String hashTicket) {
        return new ResultadoDeclaracion(true, "ANULADO", codigo, mensaje, null, null, null, hashTicket);
    }
}
