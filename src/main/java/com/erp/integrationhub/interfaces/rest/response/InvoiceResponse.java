package com.erp.integrationhub.interfaces.rest.response;

import com.erp.integrationhub.application.dto.result.InvoiceResult;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.UUID;

/**
 * REST response representing electronic voucher or invoice declaration status.
 */
public record InvoiceResponse(
        @JsonProperty("comprobanteId") UUID comprobanteId,
        @JsonProperty("exitoso") boolean successful,
        @JsonProperty("estadoSunat") String sunatStatus,
        @JsonProperty("codigoRespuestaSunat") String sunatResponseCode,
        @JsonProperty("mensajeSunat") String sunatMessage,
        @JsonProperty("pdfUrl") String pdfUrl,
        @JsonProperty("xmlUrl") String xmlUrl,
        @JsonProperty("cdrUrl") String cdrUrl,
        @JsonProperty("hashCdr") String cdrHash,
        @JsonProperty("cachedResponse") boolean cachedResponse
) {

    public static InvoiceResponse fromResult(InvoiceResult result) {
        return new InvoiceResponse(
                result.invoiceId(),
                result.successful(),
                result.sunatStatus(),
                result.sunatResponseCode(),
                result.sunatMessage(),
                result.pdfUrl(),
                result.xmlUrl(),
                result.cdrUrl(),
                result.cdrHash(),
                result.cachedResponse()
        );
    }

    // --- Backward compatibility accessors ---

    public UUID invoiceId() {
        return comprobanteId;
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
