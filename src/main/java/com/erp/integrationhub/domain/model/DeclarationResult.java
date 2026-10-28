package com.erp.integrationhub.domain.model;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serializable;

/**
 * Result of a tax declaration submitted to SUNAT or an authorized electronic billing provider (PSE/OSE).
 *
 * @param successful whether declaration succeeded or was accepted with observations
 * @param status typed invoice status
 * @param sunatResponseCode official SUNAT response code
 * @param sunatMessage descriptive message from SUNAT or PSE
 * @param pdfUrl URL of the generated PDF representation
 * @param xmlUrl URL of the signed XML file
 * @param cdrUrl URL of the SUNAT Receipt Certificate (CDR)
 * @param cdrHash cryptographic hash of the CDR
 */
public record DeclarationResult(
        @JsonProperty("successful") @JsonAlias("exitoso") boolean successful,
        @JsonProperty("status") @JsonAlias("estado") InvoiceStatus status,
        @JsonProperty("sunatResponseCode") @JsonAlias("codigoRespuestaSunat") String sunatResponseCode,
        @JsonProperty("sunatMessage") @JsonAlias("mensajeSunat") String sunatMessage,
        @JsonProperty("pdfUrl") String pdfUrl,
        @JsonProperty("xmlUrl") String xmlUrl,
        @JsonProperty("cdrUrl") String cdrUrl,
        @JsonProperty("cdrHash") @JsonAlias("hashCdr") String cdrHash
) implements Serializable {

    public static DeclarationResult accepted(String code, String message, String pdfUrl, String xmlUrl,
                                             String cdrUrl, String cdrHash) {
        return new DeclarationResult(true, InvoiceStatus.ACCEPTED, code, message, pdfUrl, xmlUrl, cdrUrl, cdrHash);
    }

    public static DeclarationResult observed(String code, String message, String pdfUrl, String xmlUrl,
                                             String cdrUrl, String cdrHash) {
        return new DeclarationResult(true, InvoiceStatus.OBSERVED, code, message, pdfUrl, xmlUrl, cdrUrl, cdrHash);
    }

    public static DeclarationResult rejected(String code, String message) {
        return new DeclarationResult(false, InvoiceStatus.REJECTED, code, message, null, null, null, null);
    }

    public static DeclarationResult pending(String message) {
        return new DeclarationResult(true, InvoiceStatus.PENDING, "0", message, null, null, null, null);
    }

    public static DeclarationResult voided(String code, String message, String hashTicket) {
        return new DeclarationResult(true, InvoiceStatus.VOIDED, code, message, null, null, null, hashTicket);
    }

    public static DeclarationResult withStatus(InvoiceStatus status, String code, String message,
                                              String pdfUrl, String xmlUrl, String cdrUrl, String cdrHash) {
        InvoiceStatus real = status != null ? status : InvoiceStatus.UNKNOWN;
        return new DeclarationResult(real.isIssued(), real, code, message, pdfUrl, xmlUrl, cdrUrl, cdrHash);
    }

    // --- Backward compatibility methods ---

    public static DeclarationResult aceptado(String codigo, String mensaje, String pdfUrl, String xmlUrl,
                                            String cdrUrl, String hashCdr) {
        return accepted(codigo, mensaje, pdfUrl, xmlUrl, cdrUrl, hashCdr);
    }

    public static DeclarationResult observado(String codigo, String mensaje, String pdfUrl, String xmlUrl,
                                             String cdrUrl, String hashCdr) {
        return observed(codigo, mensaje, pdfUrl, xmlUrl, cdrUrl, hashCdr);
    }

    public static DeclarationResult rechazado(String codigo, String mensaje) {
        return rejected(codigo, mensaje);
    }

    public static DeclarationResult pendiente(String mensaje) {
        return pending(mensaje);
    }

    public static DeclarationResult anulado(String codigo, String mensaje, String hashTicket) {
        return voided(codigo, mensaje, hashTicket);
    }

    public static DeclarationResult conEstado(InvoiceStatus estado, String codigo, String mensaje,
                                             String pdfUrl, String xmlUrl, String cdrUrl, String hashCdr) {
        return withStatus(estado, codigo, mensaje, pdfUrl, xmlUrl, cdrUrl, hashCdr);
    }

    public boolean exitoso() {
        return successful;
    }

    public InvoiceStatus estado() {
        return status;
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

    /**
     * Textual status consumed by the REST API contract.
     *
     * @return status string, preserving SUNAT regulatory status codes
     */
    public String sunatStatus() {
        if (status == null) {
            return InvoiceStatus.UNKNOWN.toSunatStatus();
        }
        return status.toSunatStatus();
    }

    /** Backward compatibility alias for sunatStatus. */
    public String estadoSunat() {
        return sunatStatus();
    }
}
