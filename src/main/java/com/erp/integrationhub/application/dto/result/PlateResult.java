package com.erp.integrationhub.application.dto.result;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serializable;

/**
 * Result DTO of querying a vehicle license plate.
 */
public record PlateResult(
        @JsonProperty("plate") @JsonAlias("placa") String plate,
        @JsonProperty("brand") @JsonAlias("marca") String brand,
        @JsonProperty("model") @JsonAlias("modelo") String model,
        @JsonProperty("vin") String vin,
        @JsonProperty("engineNumber") @JsonAlias("numeroMotor") String engineNumber,
        @JsonProperty("manufactureYear") @JsonAlias("anioFabricacion") String manufactureYear,
        @JsonProperty("color") String color,
        @JsonProperty("fuelType") @JsonAlias("combustible") String fuelType,
        @JsonProperty("valid") @JsonAlias("esValido") boolean valid,
        @JsonProperty("fromCache") boolean fromCache
) implements Serializable {

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
