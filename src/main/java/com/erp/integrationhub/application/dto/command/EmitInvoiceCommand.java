package com.erp.integrationhub.application.dto.command;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Command to emit an electronic invoice or voucher.
 */
public record EmitInvoiceCommand(
        @JsonProperty("tenantId") UUID tenantId,
        @JsonProperty("invoiceId") @JsonAlias("comprobanteId") UUID invoiceId,
        @JsonProperty("type") @JsonAlias("tipo") String type,
        @JsonProperty("series") @JsonAlias("serie") String series,
        @JsonProperty("number") @JsonAlias("numero") int number,
        @JsonProperty("customerDocType") @JsonAlias("clienteTipoDoc") String customerDocType,
        @JsonProperty("customerDocNumber") @JsonAlias("clienteNumDoc") String customerDocNumber,
        @JsonProperty("customerLegalName") @JsonAlias("clienteDenominacion") String customerLegalName,
        @JsonProperty("customerAddress") @JsonAlias("clienteDireccion") String customerAddress,
        @JsonProperty("customerEmail") @JsonAlias("clienteEmail") String customerEmail,
        @JsonProperty("currency") @JsonAlias("moneda") String currency,
        @JsonProperty("totalTaxable") @JsonAlias("totalGravada") BigDecimal totalTaxable,
        @JsonProperty("totalTax") @JsonAlias("totalIgv") BigDecimal totalTax,
        @JsonProperty("total") BigDecimal total,
        @JsonProperty("items") List<InvoiceItemCommand> items,
        @JsonProperty("issueDate") @JsonAlias("fechaEmision") LocalDate issueDate,
        @JsonProperty("idempotencyKey") String idempotencyKey
) implements Serializable {

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

    public int numero() {
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
