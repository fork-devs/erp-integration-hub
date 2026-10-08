package com.erp.integrationhub.interfaces.rest.response;

import com.erp.integrationhub.application.dto.result.PlacaResult;

public record PlacaResponse(
        String placa,
        String marca,
        String modelo,
        String vin,
        String numeroMotor,
        String anioFabricacion,
        String color,
        String combustible,
        boolean esValido,
        boolean fromCache
) {
    public static PlacaResponse fromResult(PlacaResult result) {
        return new PlacaResponse(
                result.placa(),
                result.marca(),
                result.modelo(),
                result.vin(),
                result.numeroMotor(),
                result.anioFabricacion(),
                result.color(),
                result.combustible(),
                result.esValido(),
                result.fromCache()
        );
    }
}
