package com.erp.integrationhub.application.dto.result;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serializable;
import java.util.UUID;

/**
 * Result DTO of an electronic invoice emission or status query.
 */
public record InvoiceResult(
        @JsonProperty("invoiceId") @JsonAlias("comprobanteId") UUID invoiceId,
        @JsonProperty("successful") @JsonAlias("exitoso") boolean successful,
        @JsonProperty("sunatStatus") @JsonAlias("estadoSunat") String sunatStatus,
        @JsonProperty("sunatResponseCode") @JsonAlias("codigoRespuestaSunat") String sunatResponseCode,
        @JsonProperty("sunatMessage") @JsonAlias("mensajeSunat") String sunatMessage,
        @JsonProperty("pdfUrl") String pdfUrl,
        @JsonProperty("xmlUrl") String xmlUrl,
        @JsonProperty("cdrUrl") String cdrUrl,
        @JsonProperty("cdrHash") @JsonAlias("hashCdr") String cdrHash,
        @JsonProperty("cachedResponse") boolean cachedResponse
) implements Serializable {

    // --- Backward compatibility accessors ---

    public UUID comprobanteId() {
        return invoiceId;
    }

    public boolean exitoso() {
        return successful;
    }

    public String estadoSunat() {
        return sunatStatus;
    }

    public String codigoRespuestaSunat() {
        return sunatResponseCode;
    }

    public String mensajeSunat() {
        return sunatMessage;
    }

    public String hashCdr() {
        return cdrHash;
    }
}
