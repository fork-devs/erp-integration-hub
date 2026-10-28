package com.erp.integrationhub.application.dto.result;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serializable;

/**
 * Result DTO of querying a taxpayer registration number (RUC).
 */
public record RucResult(
        @JsonProperty("ruc") String ruc,
        @JsonProperty("legalName") @JsonAlias("razonSocial") String legalName,
        @JsonProperty("status") @JsonAlias("estado") String status,
        @JsonProperty("condition") @JsonAlias("condicion") String condition,
        @JsonProperty("fiscalAddress") @JsonAlias("direccionFiscal") String fiscalAddress,
        @JsonProperty("department") @JsonAlias("departamento") String department,
        @JsonProperty("province") @JsonAlias("provincia") String province,
        @JsonProperty("district") @JsonAlias("distrito") String district,
        @JsonProperty("ubigeo") String ubigeo,
        @JsonProperty("valid") @JsonAlias("esValido") boolean valid,
        @JsonProperty("fromCache") boolean fromCache
) implements Serializable {

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
