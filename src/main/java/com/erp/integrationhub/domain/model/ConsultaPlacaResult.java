package com.erp.integrationhub.domain.model;

import java.io.Serializable;

public record ConsultaPlacaResult(
        String placa,
        String marca,
        String modelo,
        String vin,
        String numeroMotor,
        String anioFabricacion,
        String color,
        String combustible,
        boolean esValido
) implements Serializable {
    public static ConsultaPlacaResult invalido(String placa) {
        return new ConsultaPlacaResult(placa, null, null, null, null, null, null, null, false);
    }
}
