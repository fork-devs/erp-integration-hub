package com.erp.integrationhub.domain.model;

import java.io.Serializable;

public record ResultadoPago(
        boolean exitoso,
        String estado, // SUCCEEDED, REQUIRES_ACTION, FAILED, PENDING
        String chargeId,
        String clientSecret,
        String codigoAutorizacion,
        String mensajeError
) implements Serializable {
    public static ResultadoPago exitoso(String chargeId, String codigoAutorizacion) {
        return new ResultadoPago(true, "SUCCEEDED", chargeId, null, codigoAutorizacion, null);
    }

    public static ResultadoPago requiereAccion(String chargeId, String clientSecret) {
        return new ResultadoPago(true, "REQUIRES_ACTION", chargeId, clientSecret, null, null);
    }

    public static ResultadoPago fallido(String mensajeError) {
        return new ResultadoPago(false, "FAILED", null, null, null, mensajeError);
    }
}
