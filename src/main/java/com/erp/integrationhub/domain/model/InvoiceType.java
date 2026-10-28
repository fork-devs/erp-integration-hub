package com.erp.integrationhub.domain.model;

/**
 * Supported electronic invoice/voucher types according to SUNAT catalog 01.
 */
public enum InvoiceType {
    FACTURA("01"),
    BOLETA("03"),
    NOTA_CREDITO("07"),
    NOTA_DEBITO("08");

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
}
