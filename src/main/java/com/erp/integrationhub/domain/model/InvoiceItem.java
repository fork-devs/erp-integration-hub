package com.erp.integrationhub.domain.model;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * Line item of an electronic voucher or invoice.
 *
 * @param unitOfMeasure unit of measure according to SUNAT catalog (e.g., 'NIU' for units, 'ZZ' for services)
 * @param description item description or service concept
 * @param quantity item quantity, must be greater than zero
 * @param unitValue unit value before tax
 * @param unitPrice unit price inclusive of tax (IGV)
 * @param taxAmount total IGV (tax) for this item line
 * @param total line total inclusive of tax
 */
public record InvoiceItem(
        @JsonProperty("unitOfMeasure") @JsonAlias("unidadMedida") String unitOfMeasure,
        @JsonProperty("description") @JsonAlias("descripcion") String description,
        @JsonProperty("quantity") @JsonAlias("cantidad") BigDecimal quantity,
        @JsonProperty("unitValue") @JsonAlias("valorUnitario") BigDecimal unitValue,
        @JsonProperty("unitPrice") @JsonAlias("precioUnitario") BigDecimal unitPrice,
        @JsonProperty("taxAmount") @JsonAlias({"taxAmount", "igv"}) BigDecimal taxAmount,
        @JsonProperty("total") BigDecimal total
) implements Serializable {

    public InvoiceItem {
        if (description == null || description.trim().isEmpty()) {
            throw new IllegalArgumentException("Item description cannot be empty");
        }
        if (quantity == null || quantity.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Quantity must be greater than zero");
        }
        if (unitOfMeasure == null || unitOfMeasure.trim().isEmpty()) {
            unitOfMeasure = "NIU";
        }
    }

    /** Backward compatibility accessor for unidadMedida. */
    public String unidadMedida() {
        return unitOfMeasure;
    }

    /** Backward compatibility accessor for descripcion. */
    public String descripcion() {
        return description;
    }

    /** Backward compatibility accessor for cantidad. */
    public BigDecimal cantidad() {
        return quantity;
    }

    /** Backward compatibility accessor for valorUnitario. */
    public BigDecimal valorUnitario() {
        return unitValue;
    }

    /** Backward compatibility accessor for precioUnitario. */
    public BigDecimal precioUnitario() {
        return unitPrice;
    }

    /** Backward compatibility accessor for igv. */
    public BigDecimal igv() {
        return taxAmount;
    }
}
