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

        @NotBlank(message = "{validation.hub.void-invoice.type.not-blank}")
        @JsonProperty("type")
        @JsonAlias("tipo")
        String type,

        @NotBlank(message = "{validation.hub.void-invoice.series.not-blank}")
        @JsonProperty("series")
        @JsonAlias("serie")
        String series,

        @NotNull(message = "{validation.hub.void-invoice.number.not-null}")
        @JsonProperty("number")
        @JsonAlias("numero")
        Integer number,

        @NotBlank(message = "{validation.hub.void-invoice.reason.not-blank}")
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
