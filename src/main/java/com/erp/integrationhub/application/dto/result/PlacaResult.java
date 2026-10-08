package com.erp.integrationhub.application.dto.result;

public record PlacaResult(
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
) {}
