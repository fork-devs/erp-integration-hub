package com.erp.integrationhub.interfaces.rest.request;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * REST request to void an electronic invoice or voucher.
 */
public record VoidInvoiceRequest(
        @JsonProperty("invoiceId")
        @JsonAlias("comprobanteId")
        UUID invoiceId,

        @NotBlank(message = "Document type is required (factura, boleta, nota_credito, nota_debito)")
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

        @NotBlank(message = "Voiding reason is required")
        @JsonProperty("reason")
        @JsonAlias("motivo")
        String reason
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

    public String motivo() {
        return reason;
    }
}
