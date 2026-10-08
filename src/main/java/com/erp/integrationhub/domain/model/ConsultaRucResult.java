package com.erp.integrationhub.domain.model;

import java.io.Serializable;

public record ConsultaRucResult(
        String ruc,
        String razonSocial,
        String estado,     // ACTIVO, BAJA, etc.
        String condicion,  // HABIDO, NO HABIDO
        String direccionFiscal,
        String departamento,
        String provincia,
        String distrito,
        String ubigeo,
        boolean esValido
) implements Serializable {
    public static ConsultaRucResult invalido(String ruc) {
        return new ConsultaRucResult(ruc, null, null, null, null, null, null, null, null, false);
    }
}
