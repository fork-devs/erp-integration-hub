package com.erp.integrationhub.domain.model;

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
     * @return 2-digit SUNAT code (e.g., "01" for Invoice, "03" for Receipt)
     */
    public String getSunatCode() {
        return sunatCode;
    }
}
