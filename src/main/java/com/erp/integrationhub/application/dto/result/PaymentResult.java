package com.erp.integrationhub.application.dto.result;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serializable;
import java.util.UUID;

/**
 * Result DTO of a payment transaction execution.
 */
public record PaymentResult(
        @JsonProperty("paymentId") UUID paymentId,
        @JsonProperty("successful") @JsonAlias("exitoso") boolean successful,
        @JsonProperty("status") @JsonAlias("estado") String status,
        @JsonProperty("chargeId") String chargeId,
        @JsonProperty("clientSecret") String clientSecret,
        @JsonProperty("authorizationCode") @JsonAlias("codigoAutorizacion") String authorizationCode,
        @JsonProperty("errorMessage") @JsonAlias("mensajeError") String errorMessage,
        @JsonProperty("cachedResponse") boolean cachedResponse
) implements Serializable {

    // --- Backward compatibility accessors ---

    public boolean exitoso() {
        return successful;
    }

    public String estado() {
        return status;
    }

    public String codigoAutorizacion() {
        return authorizationCode;
    }

    public String mensajeError() {
        return errorMessage;
    }
}
