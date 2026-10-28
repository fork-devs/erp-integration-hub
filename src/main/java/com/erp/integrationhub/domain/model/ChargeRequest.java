package com.erp.integrationhub.domain.model;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * Domain entity representing an outbound payment charge request to a gateway (Stripe).
 */
public class ChargeRequest implements Serializable {
    private final UUID tenantId;
    private final UUID paymentId;
    private final BigDecimal amount;
    private final Currency currency;
    private final String cardToken;
    private final String description;
    private final String customerEmail;
    private final String idempotencyKey;

    public ChargeRequest(
            UUID tenantId,
            UUID paymentId,
            BigDecimal amount,
            Currency currency,
            String cardToken,
            String description,
            String customerEmail,
            String idempotencyKey) {
        if (tenantId == null || paymentId == null || amount == null || amount.compareTo(BigDecimal.ZERO) <= 0 || idempotencyKey == null) {
            throw new IllegalArgumentException("Required charge request fields cannot be null");
        }
        this.tenantId = tenantId;
        this.paymentId = paymentId;
        this.amount = amount;
        this.currency = currency != null ? currency : Currency.PEN;
        this.cardToken = cardToken != null ? cardToken.trim() : "";
        this.description = description != null ? description.trim() : "Automotive service payment";
        this.customerEmail = customerEmail != null ? customerEmail.trim() : "";
        this.idempotencyKey = idempotencyKey.trim();
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public UUID getPaymentId() {
        return paymentId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public Currency getCurrency() {
        return currency;
    }

    public String getCardToken() {
        return cardToken;
    }

    public String getDescription() {
        return description;
    }

    public String getCustomerEmail() {
        return customerEmail;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    // --- Backward compatibility accessors ---

    public BigDecimal getMonto() {
        return amount;
    }

    public Currency getMoneda() {
        return currency;
    }

    public String getTokenTarjeta() {
        return cardToken;
    }

    public String getDescripcion() {
        return description;
    }

    public String getClienteEmail() {
        return customerEmail;
    }
}
