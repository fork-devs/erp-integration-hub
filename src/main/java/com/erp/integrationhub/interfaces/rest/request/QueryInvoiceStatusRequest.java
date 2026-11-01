package com.erp.integrationhub.interfaces.rest.request;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * REST request to query status of an electronic document in SUNAT.
 */
public record QueryInvoiceStatusRequest(
        @JsonProperty("invoiceId")
        @JsonAlias("comprobanteId")
        UUID invoiceId,

        @NotBlank(message = "{validation.hub.query-invoice.type.not-blank}")
        @JsonProperty("type")
        @JsonAlias("tipo")
        String type,

        @NotBlank(message = "{validation.hub.query-invoice.series.not-blank}")
        @JsonProperty("series")
        @JsonAlias("serie")
        String series,

        @NotNull(message = "{validation.hub.query-invoice.number.not-null}")
        @JsonProperty("number")
        @JsonAlias("numero")
        Integer number
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
}
