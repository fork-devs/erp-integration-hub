package com.erp.integrationhub.interfaces.rest.response;

import com.erp.integrationhub.application.dto.result.PaymentResult;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.UUID;

/**
 * REST response representing payment execution outcome.
 */
public record PaymentResponse(
        @JsonProperty("paymentId") UUID paymentId,
        @JsonProperty("exitoso") boolean successful,
        @JsonProperty("estado") String status,
        @JsonProperty("chargeId") String chargeId,
        @JsonProperty("clientSecret") String clientSecret,
        @JsonProperty("codigoAutorizacion") String authorizationCode,
        @JsonProperty("mensajeError") String errorMessage,
        @JsonProperty("cachedResponse") boolean cachedResponse
) {

    public static PaymentResponse fromResult(PaymentResult result) {
        return new PaymentResponse(
                result.paymentId(),
                result.successful(),
                result.status(),
                result.chargeId(),
                result.clientSecret(),
                result.authorizationCode(),
                result.errorMessage(),
                result.cachedResponse()
        );
    }

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
