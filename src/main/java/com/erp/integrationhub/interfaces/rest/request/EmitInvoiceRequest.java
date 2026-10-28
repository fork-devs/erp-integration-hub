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

        @NotBlank(message = "Invoice type is required (FACTURA, BOLETA, NOTA_CREDITO)")
        @JsonProperty("type")
        @JsonAlias("tipo")
        String type,

        @NotBlank(message = "Series is required (e.g., F001, B001)")
        @JsonProperty("series")
        @JsonAlias("serie")
        String series,

        @NotNull(message = "Sequential document number is required")
        @JsonProperty("number")
        @JsonAlias("numero")
        Integer number,

        @NotBlank(message = "Customer document type is required (6=RUC, 1=DNI)")
        @JsonProperty("customerDocType")
        @JsonAlias("clienteTipoDoc")
        String customerDocType,

        @NotBlank(message = "Customer document number is required")
        @JsonProperty("customerDocNumber")
        @JsonAlias("clienteNumDoc")
        String customerDocNumber,

        @NotBlank(message = "Customer legal name is required")
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

        @NotNull(message = "Taxable total is required")
        @DecimalMin(value = "0.0", inclusive = true, message = "Taxable total cannot be negative")
        @JsonProperty("totalTaxable")
        @JsonAlias("totalGravada")
        BigDecimal totalTaxable,

        @NotNull(message = "Total tax (IGV) is required")
        @DecimalMin(value = "0.0", inclusive = true, message = "Total tax cannot be negative")
        @JsonProperty("totalTax")
        @JsonAlias("totalIgv")
        BigDecimal totalTax,

        @NotNull(message = "Total is required")
        @DecimalMin(value = "0.01", message = "Total must be greater than 0")
        @JsonProperty("total")
        BigDecimal total,

        @NotEmpty(message = "Invoice must contain at least one line item")
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
