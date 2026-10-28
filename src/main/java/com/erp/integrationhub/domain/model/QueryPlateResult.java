package com.erp.integrationhub.domain.model;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serializable;

/**
 * Result of querying a vehicle license plate against the official registry (SUNARP).
 *
 * @param plate vehicle license plate
 * @param brand vehicle make / brand
 * @param model vehicle model
 * @param vin Vehicle Identification Number (chassis)
 * @param engineNumber vehicle engine number
 * @param manufactureYear year of manufacture
 * @param color vehicle primary color
 * @param fuelType fuel / propulsion type
 * @param valid whether the query returned a valid vehicle record
 */
public record QueryPlateResult(
        @JsonProperty("plate") @JsonAlias("placa") String plate,
        @JsonProperty("brand") @JsonAlias("marca") String brand,
        @JsonProperty("model") @JsonAlias("modelo") String model,
        @JsonProperty("vin") String vin,
        @JsonProperty("engineNumber") @JsonAlias("numeroMotor") String engineNumber,
        @JsonProperty("manufactureYear") @JsonAlias("anioFabricacion") String manufactureYear,
        @JsonProperty("color") String color,
        @JsonProperty("fuelType") @JsonAlias("combustible") String fuelType,
        @JsonProperty("valid") @JsonAlias("esValido") boolean valid
) implements Serializable {

    public static QueryPlateResult invalid(String plate) {
        return new QueryPlateResult(plate, null, null, null, null, null, null, null, false);
    }

    // --- Backward compatibility methods ---

    public static QueryPlateResult invalido(String placa) {
        return invalid(placa);
    }

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
