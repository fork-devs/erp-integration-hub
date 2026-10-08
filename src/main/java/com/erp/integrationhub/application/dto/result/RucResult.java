package com.erp.integrationhub.application.dto.result;

public record RucResult(
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
) {}
