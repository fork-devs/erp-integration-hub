package com.erp.integrationhub.interfaces.rest.response;

import com.erp.integrationhub.application.dto.result.RucResult;

public record RucResponse(
        String ruc,
        String razonSocial,
        String estado,
        String condicion,
        String direccionFiscal,
        String departamento,
        String provincia,
        String distrito,
        String ubigeo,
        boolean esValido,
        boolean fromCache
) {
    public static RucResponse fromResult(RucResult result) {
        return new RucResponse(
                result.ruc(),
                result.razonSocial(),
                result.estado(),
                result.condicion(),
                result.direccionFiscal(),
                result.departamento(),
                result.provincia(),
                result.distrito(),
                result.ubigeo(),
                result.esValido(),
                result.fromCache()
        );
    }
}
