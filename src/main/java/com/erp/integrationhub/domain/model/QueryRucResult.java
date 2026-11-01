package com.erp.integrationhub.domain.model;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serializable;

/**
 * Result of querying a taxpayer registration number (RUC) against SUNAT.
 *
 * @param ruc 11-digit RUC number
 * @param legalName corporate or individual legal name
 * @param status taxpayer status (ACTIVO, BAJA, etc.)
 * @param condition taxpayer fiscal condition (HABIDO, NO HABIDO, etc.)
 * @param fiscalAddress registered fiscal address
 * @param department department / state
 * @param province province
 * @param district district
 * @param ubigeo 6-digit geographic code
 * @param valid whether the RUC record was found and valid
 */
public record QueryRucResult(
        @JsonProperty("ruc") String ruc,
        @JsonProperty("legalName") @JsonAlias("razonSocial") String legalName,
        @JsonProperty("status") @JsonAlias("estado") String status,
        @JsonProperty("condition") @JsonAlias("condicion") String condition,
        @JsonProperty("fiscalAddress") @JsonAlias("direccionFiscal") String fiscalAddress,
        @JsonProperty("department") @JsonAlias("departamento") String department,
        @JsonProperty("province") @JsonAlias("provincia") String province,
        @JsonProperty("district") @JsonAlias("distrito") String district,
        @JsonProperty("ubigeo") String ubigeo,
        @JsonProperty("valid") @JsonAlias("esValido") boolean valid
) implements Serializable {

    public static QueryRucResult invalid(String ruc) {
        return new QueryRucResult(ruc, null, null, null, null, null, null, null, null, false);
    }

    // --- Backward compatibility methods ---

    public static QueryRucResult invalido(String ruc) {
        return invalid(ruc);
    }

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
