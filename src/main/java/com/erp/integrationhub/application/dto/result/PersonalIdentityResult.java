package com.erp.integrationhub.application.dto.result;

import com.erp.integrationhub.domain.model.QueryPersonalIdentityResult;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serializable;

/**
 * Result DTO for personal identity document query (DNI / CE).
 */
public record PersonalIdentityResult(
        @JsonProperty("documentType") String documentType,
        @JsonProperty("documentNumber") String documentNumber,
        @JsonProperty("firstName") String firstName,
        @JsonProperty("firstSurname") String firstSurname,
        @JsonProperty("secondSurname") String secondSurname,
        @JsonProperty("fullName") String fullName,
        @JsonProperty("verificationCode") String verificationCode,
        @JsonProperty("valid") boolean valid,
        @JsonProperty("fromCache") boolean fromCache
) implements Serializable {

    public static PersonalIdentityResult fromDomain(QueryPersonalIdentityResult domain, boolean fromCache) {
        return new PersonalIdentityResult(
                domain.documentType(),
                domain.documentNumber(),
                domain.firstName(),
                domain.firstSurname(),
                domain.secondSurname(),
                domain.fullName(),
                domain.verificationCode(),
                domain.valid(),
                fromCache
        );
    }
}
