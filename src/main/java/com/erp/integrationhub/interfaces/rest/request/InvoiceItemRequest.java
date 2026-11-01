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
        @NotBlank(message = "{validation.hub.invoice-item.unit-of-measure.not-blank}")
        @JsonProperty("unitOfMeasure")
        @JsonAlias("unidadMedida")
        String unitOfMeasure,

        @NotBlank(message = "{validation.hub.invoice-item.description.not-blank}")
        @JsonProperty("description")
        @JsonAlias("descripcion")
        String description,

        @NotNull(message = "{validation.hub.invoice-item.quantity.not-null}")
        @DecimalMin(value = "0.01", message = "{validation.hub.invoice-item.quantity.min}")
        @JsonProperty("quantity")
        @JsonAlias("cantidad")
        BigDecimal quantity,

        @NotNull(message = "{validation.hub.invoice-item.unit-value.not-null}")
        @DecimalMin(value = "0.0", inclusive = true, message = "{validation.hub.invoice-item.unit-value.min}")
        @JsonProperty("unitValue")
        @JsonAlias("valorUnitario")
        BigDecimal unitValue,

        @NotNull(message = "{validation.hub.invoice-item.unit-price.not-null}")
        @DecimalMin(value = "0.0", inclusive = true, message = "{validation.hub.invoice-item.unit-price.min}")
        @JsonProperty("unitPrice")
        @JsonAlias("precioUnitario")
        BigDecimal unitPrice,

        @NotNull(message = "{validation.hub.invoice-item.tax-amount.not-null}")
        @DecimalMin(value = "0.0", inclusive = true, message = "{validation.hub.invoice-item.tax-amount.min}")
        @JsonProperty("taxAmount")
        @JsonAlias({"igv", "taxAmount"})
        BigDecimal taxAmount,

        @NotNull(message = "{validation.hub.invoice-item.total.not-null}")
        @DecimalMin(value = "0.0", inclusive = true, message = "{validation.hub.invoice-item.total.min}")
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
