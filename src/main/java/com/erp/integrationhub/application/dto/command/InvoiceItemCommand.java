package com.erp.integrationhub.application.dto.command;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * Command representing an invoice line item.
 */
public record InvoiceItemCommand(
        @JsonProperty("unitOfMeasure") @JsonAlias("unidadMedida") String unitOfMeasure,
        @JsonProperty("description") @JsonAlias("descripcion") String description,
        @JsonProperty("quantity") @JsonAlias("cantidad") BigDecimal quantity,
        @JsonProperty("unitValue") @JsonAlias("valorUnitario") BigDecimal unitValue,
        @JsonProperty("unitPrice") @JsonAlias("precioUnitario") BigDecimal unitPrice,
        @JsonProperty("taxAmount") @JsonAlias({"taxAmount", "igv"}) BigDecimal taxAmount,
        @JsonProperty("total") BigDecimal total
) implements Serializable {

    // --- Backward compatibility accessors ---

    public String unidadMedida() {
        return unitOfMeasure;
    }

    public String descripcion() {
        return description;
    }

    public BigDecimal cantidad() {
        return quantity;
    }

    public BigDecimal valorUnitario() {
        return unitValue;
    }

    public BigDecimal precioUnitario() {
        return unitPrice;
    }

    public BigDecimal igv() {
        return taxAmount;
    }
}
