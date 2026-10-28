package com.erp.integrationhub.domain.model;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serializable;

/**
 * Result of a payment transaction executed through a payment gateway.
 *
 * @param successful whether the transaction succeeded
 * @param status status returned by the payment gateway (SUCCEEDED, REQUIRES_ACTION, FAILED, PENDING)
 * @param chargeId identifier assigned by the payment provider
 * @param clientSecret secret token for client-side 3DS authentication if required
 * @param authorizationCode bank authorization or verification code
 * @param errorMessage error detail if transaction failed
 */
public record PaymentOutcome(
        @JsonProperty("successful") @JsonAlias("exitoso") boolean successful,
        @JsonProperty("status") @JsonAlias("estado") String status,
        @JsonProperty("chargeId") String chargeId,
        @JsonProperty("clientSecret") String clientSecret,
        @JsonProperty("authorizationCode") @JsonAlias("codigoAutorizacion") String authorizationCode,
        @JsonProperty("errorMessage") @JsonAlias("mensajeError") String errorMessage
) implements Serializable {

    public static PaymentOutcome success(String chargeId, String authorizationCode) {
        return new PaymentOutcome(true, "SUCCEEDED", chargeId, null, authorizationCode, null);
    }

    public static PaymentOutcome requiresAction(String chargeId, String clientSecret) {
        return new PaymentOutcome(true, "REQUIRES_ACTION", chargeId, clientSecret, null, null);
    }

    public static PaymentOutcome failure(String errorMessage) {
        return new PaymentOutcome(false, "FAILED", null, null, null, errorMessage);
    }

    // --- Backward compatibility factory methods and accessors ---

    public static PaymentOutcome exitoso(String chargeId, String codigoAutorizacion) {
        return success(chargeId, codigoAutorizacion);
    }

    public static PaymentOutcome requiereAccion(String chargeId, String clientSecret) {
        return requiresAction(chargeId, clientSecret);
    }

    public static PaymentOutcome fallido(String mensajeError) {
        return failure(mensajeError);
    }

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
