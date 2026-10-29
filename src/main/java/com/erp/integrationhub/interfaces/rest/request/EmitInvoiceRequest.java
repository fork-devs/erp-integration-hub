package com.erp.integrationhub.interfaces.rest.request;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * REST request to emit an electronic invoice or voucher to SUNAT.
 */
public record EmitInvoiceRequest(
        @JsonProperty("invoiceId")
        @JsonAlias("comprobanteId")
        UUID invoiceId,

        @NotBlank(message = "{validation.hub.invoice.type.not-blank}")
        @JsonProperty("type")
        @JsonAlias("tipo")
        String type,

        @NotBlank(message = "{validation.hub.invoice.series.not-blank}")
        @JsonProperty("series")
        @JsonAlias("serie")
        String series,

        @NotNull(message = "{validation.hub.invoice.number.not-null}")
        @JsonProperty("number")
        @JsonAlias("numero")
        Integer number,

        @NotBlank(message = "{validation.hub.invoice.customer-doc-type.not-blank}")
        @JsonProperty("customerDocType")
        @JsonAlias("clienteTipoDoc")
        String customerDocType,

        @NotBlank(message = "{validation.hub.invoice.customer-doc-number.not-blank}")
        @JsonProperty("customerDocNumber")
        @JsonAlias("clienteNumDoc")
        String customerDocNumber,

        @NotBlank(message = "{validation.hub.invoice.customer-legal-name.not-blank}")
        @JsonProperty("customerLegalName")
        @JsonAlias("clienteDenominacion")
        String customerLegalName,

        @JsonProperty("customerAddress")
        @JsonAlias("clienteDireccion")
        String customerAddress,

        @JsonProperty("customerEmail")
        @JsonAlias("clienteEmail")
        String customerEmail,

        @JsonProperty("currency")
        @JsonAlias("moneda")
        String currency,

        @NotNull(message = "{validation.hub.invoice.total-taxable.not-null}")
        @DecimalMin(value = "0.0", inclusive = true, message = "{validation.hub.invoice.total-taxable.min}")
        @JsonProperty("totalTaxable")
        @JsonAlias("totalGravada")
        BigDecimal totalTaxable,

        @NotNull(message = "{validation.hub.invoice.total-tax.not-null}")
        @DecimalMin(value = "0.0", inclusive = true, message = "{validation.hub.invoice.total-tax.min}")
        @JsonProperty("totalTax")
        @JsonAlias("totalIgv")
        BigDecimal totalTax,

        @NotNull(message = "{validation.hub.invoice.total.not-null}")
        @DecimalMin(value = "0.01", message = "{validation.hub.invoice.total.min}")
        @JsonProperty("total")
        BigDecimal total,

        @NotEmpty(message = "{validation.hub.invoice.items.not-empty}")
        @Valid
        @JsonProperty("items")
        List<InvoiceItemRequest> items,

        @JsonProperty("issueDate")
        @JsonAlias("fechaEmision")
        LocalDate issueDate
) {

    // --- Backward compatibility accessors ---

    public UUID comprobanteId() {
        return invoiceId;
    }

    public String tipo() {
        return type;
    }

    public String serie() {
        return series;
    }

    public Integer numero() {
        return number;
    }

    public String clienteTipoDoc() {
        return customerDocType;
    }

    public String clienteNumDoc() {
        return customerDocNumber;
    }

    public String clienteDenominacion() {
        return customerLegalName;
    }

    public String clienteDireccion() {
        return customerAddress;
    }

    public String clienteEmail() {
        return customerEmail;
    }

    public String moneda() {
        return currency;
    }

    public BigDecimal totalGravada() {
        return totalTaxable;
    }

    public BigDecimal totalIgv() {
        return totalTax;
    }

    public LocalDate fechaEmision() {
        return issueDate;
    }
}
