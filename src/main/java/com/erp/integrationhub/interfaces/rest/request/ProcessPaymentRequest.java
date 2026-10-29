package com.erp.integrationhub.interfaces.rest.request;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * REST request to process a card charge via payment gateway.
 */
public record ProcessPaymentRequest(
        @JsonProperty("paymentId")
        UUID paymentId,

        @NotNull(message = "{validation.hub.payment.amount.not-null}")
        @DecimalMin(value = "0.50", message = "{validation.hub.payment.amount.min}")
        @JsonProperty("amount")
        @JsonAlias("monto")
        BigDecimal amount,

        @JsonProperty("currency")
        @JsonAlias("moneda")
        String currency,

        @NotBlank(message = "{validation.hub.payment.card-token.not-blank}")
        @JsonProperty("cardToken")
        @JsonAlias("tokenTarjeta")
        String cardToken,

        @NotBlank(message = "{validation.hub.payment.description.not-blank}")
        @JsonProperty("description")
        @JsonAlias("descripcion")
        String description,

        @JsonProperty("customerEmail")
        @JsonAlias("clienteEmail")
        String customerEmail
) {

    // --- Backward compatibility accessors ---

    public BigDecimal monto() {
        return amount;
    }

    public String moneda() {
        return currency;
    }

    public String tokenTarjeta() {
        return cardToken;
    }

    public String descripcion() {
        return description;
    }

    public String clienteEmail() {
        return customerEmail;
    }
}
