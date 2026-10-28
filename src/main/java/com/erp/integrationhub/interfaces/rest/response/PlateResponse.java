package com.erp.integrationhub.interfaces.rest.response;

import com.erp.integrationhub.application.dto.result.PlateResult;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * REST response representing vehicle license plate query results.
 */
public record PlateResponse(
        @JsonProperty("placa") String plate,
        @JsonProperty("marca") String brand,
        @JsonProperty("modelo") String model,
        @JsonProperty("vin") String vin,
        @JsonProperty("numeroMotor") String engineNumber,
        @JsonProperty("anioFabricacion") String manufactureYear,
        @JsonProperty("color") String color,
        @JsonProperty("combustible") String fuelType,
        @JsonProperty("esValido") boolean valid,
        @JsonProperty("fromCache") boolean fromCache
) {

    public static PlateResponse fromResult(PlateResult result) {
        return new PlateResponse(
                result.plate(),
                result.brand(),
                result.model(),
                result.vin(),
                result.engineNumber(),
                result.manufactureYear(),
                result.color(),
                result.fuelType(),
                result.valid(),
                result.fromCache()
        );
    }

    // --- Backward compatibility accessors ---

    public String placa() {
        return plate;
    }

    public String marca() {
        return brand;
    }

    public String modelo() {
        return model;
    }

    public String numeroMotor() {
        return engineNumber;
    }

    public String anioFabricacion() {
        return manufactureYear;
    }

    public String combustible() {
        return fuelType;
    }

    public boolean esValido() {
        return valid;
    }
}
