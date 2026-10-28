package com.erp.integrationhub.application.dto.command;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * Command to process a credit/debit card payment charge.
 */
public record ProcessPaymentCommand(
        @JsonProperty("tenantId") UUID tenantId,
        @JsonProperty("paymentId") UUID paymentId,
        @JsonProperty("amount") @JsonAlias("monto") BigDecimal amount,
        @JsonProperty("currency") @JsonAlias("moneda") String currency,
        @JsonProperty("cardToken") @JsonAlias("tokenTarjeta") String cardToken,
        @JsonProperty("description") @JsonAlias("descripcion") String description,
        @JsonProperty("customerEmail") @JsonAlias("clienteEmail") String customerEmail,
        @JsonProperty("idempotencyKey") String idempotencyKey
) implements Serializable {

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
