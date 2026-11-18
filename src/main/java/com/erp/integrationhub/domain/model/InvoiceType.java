package com.erp.integrationhub.domain.model;

import com.fasterxml.jackson.annotation.JsonCreator;

/**
 * Supported electronic invoice/voucher types according to SUNAT catalog 01.
 */
public enum InvoiceType {
    INVOICE("01"),
    RECEIPT("03"),
    CREDIT_NOTE("07"),
    DEBIT_NOTE("08");

    private final String sunatCode;

    InvoiceType(String sunatCode) {
        this.sunatCode = sunatCode;
    }

    /**
     * Returns the 2-digit SUNAT catalog code for this invoice type.
     *
     * @return 2-digit SUNAT code (e.g., "01" for Factura, "03" for Boleta)
     */
    public String getSunatCode() {
        return sunatCode;
    }

    /**
     * Backward-compatible alias for getSunatCode.
     *
     * @return 2-digit SUNAT code
     */
    public String getCodigoSunat() {
        return sunatCode;
    }

    @JsonCreator
    public static InvoiceType fromString(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Invoice type cannot be null or blank");
        }
        return switch (value.trim().toUpperCase()) {
            case "INVOICE", "FACTURA" -> INVOICE;
            case "RECEIPT", "BOLETA" -> RECEIPT;
            case "CREDIT_NOTE", "NOTA_CREDITO" -> CREDIT_NOTE;
            case "DEBIT_NOTE", "NOTA_DEBITO" -> DEBIT_NOTE;
            default -> throw new IllegalArgumentException("Unknown invoice type: " + value);
        };
    }
}
