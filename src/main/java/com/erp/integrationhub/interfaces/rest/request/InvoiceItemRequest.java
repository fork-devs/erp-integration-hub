package com.erp.integrationhub.interfaces.rest.request;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

/**
 * REST request line item for invoice emission.
 */
public record InvoiceItemRequest(
        @NotBlank(message = "Unit of measure is required (e.g., NIU, ZZ)")
        @JsonProperty("unitOfMeasure")
        @JsonAlias("unidadMedida")
        String unitOfMeasure,

        @NotBlank(message = "Item description is required")
        @JsonProperty("description")
        @JsonAlias("descripcion")
        String description,

        @NotNull(message = "Quantity is required")
        @DecimalMin(value = "0.01", message = "Quantity must be greater than 0")
        @JsonProperty("quantity")
        @JsonAlias("cantidad")
        BigDecimal quantity,

        @NotNull(message = "Unit value before tax is required")
        @DecimalMin(value = "0.0", inclusive = true, message = "Unit value cannot be negative")
        @JsonProperty("unitValue")
        @JsonAlias("valorUnitario")
        BigDecimal unitValue,

        @NotNull(message = "Unit price inclusive of tax is required")
        @DecimalMin(value = "0.0", inclusive = true, message = "Unit price cannot be negative")
        @JsonProperty("unitPrice")
        @JsonAlias("precioUnitario")
        BigDecimal unitPrice,

        @NotNull(message = "Tax amount (IGV) is required")
        @DecimalMin(value = "0.0", inclusive = true, message = "Tax amount cannot be negative")
        @JsonProperty("taxAmount")
        @JsonAlias({"igv", "taxAmount"})
        BigDecimal taxAmount,

        @NotNull(message = "Total is required")
        @DecimalMin(value = "0.0", inclusive = true, message = "Total cannot be negative")
        @JsonProperty("total")
        BigDecimal total
) {

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
