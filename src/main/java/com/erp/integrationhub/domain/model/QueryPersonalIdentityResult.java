package com.erp.integrationhub.domain.model;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serializable;

/**
 * Result of querying an individual identity document (DNI / Carnet de Extranjería) against official registry.
 *
 * @param documentType document type (DNI, CE)
 * @param documentNumber document identification number
 * @param firstName given name(s)
 * @param firstSurname paternal surname (apellido paterno)
 * @param secondSurname maternal surname (apellido materno)
 * @param fullName full formatted legal name
 * @param verificationCode verification digit if present
 * @param valid whether the document record was found and valid
 */
public record QueryPersonalIdentityResult(
        @JsonProperty("documentType") String documentType,
        @JsonProperty("documentNumber") @JsonAlias("numero") String documentNumber,
        @JsonProperty("firstName") @JsonAlias("nombres") String firstName,
        @JsonProperty("firstSurname") @JsonAlias({"apellidoPaterno", "apellido_paterno"}) String firstSurname,
        @JsonProperty("secondSurname") @JsonAlias({"apellidoMaterno", "apellido_materno"}) String secondSurname,
        @JsonProperty("fullName") @JsonAlias({"nombreCompleto", "nombre_completo"}) String fullName,
        @JsonProperty("verificationCode") @JsonAlias({"codigoVerificacion", "codigo_verificacion"}) String verificationCode,
        @JsonProperty("valid") boolean valid
) implements Serializable {

    public static QueryPersonalIdentityResult invalid(String documentType, String documentNumber) {
        return new QueryPersonalIdentityResult(documentType, documentNumber, null, null, null, null, null, false);
    }
}
