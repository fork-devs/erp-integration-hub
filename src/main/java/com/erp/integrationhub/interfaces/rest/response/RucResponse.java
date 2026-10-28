package com.erp.integrationhub.interfaces.rest.response;

import com.erp.integrationhub.application.dto.result.RucResult;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * REST response representing taxpayer (RUC) validation results.
 */
public record RucResponse(
        @JsonProperty("ruc") String ruc,
        @JsonProperty("razonSocial") String legalName,
        @JsonProperty("estado") String status,
        @JsonProperty("condicion") String condition,
        @JsonProperty("direccionFiscal") String fiscalAddress,
        @JsonProperty("departamento") String department,
        @JsonProperty("provincia") String province,
        @JsonProperty("distrito") String district,
        @JsonProperty("ubigeo") String ubigeo,
        @JsonProperty("esValido") boolean valid,
        @JsonProperty("fromCache") boolean fromCache
) {
    public static RucResponse fromResult(RucResult result) {
        return new RucResponse(
                result.ruc(),
                result.legalName(),
                result.status(),
                result.condition(),
                result.fiscalAddress(),
                result.department(),
                result.province(),
                result.district(),
                result.ubigeo(),
                result.valid(),
                result.fromCache()
        );
    }

    // --- Backward compatibility accessors ---

    public String razonSocial() {
        return legalName;
    }

    public String estado() {
        return status;
    }

    public String condicion() {
        return condition;
    }

    public String direccionFiscal() {
        return fiscalAddress;
    }

    public String departamento() {
        return department;
    }

    public String provincia() {
        return province;
    }

    public String distrito() {
        return district;
    }

    public boolean esValido() {
        return valid;
    }
}
