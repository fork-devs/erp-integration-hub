package com.erp.integrationhub.domain.model;

/**
 * Currency enumeration supported by payment and electronic invoicing operations.
 */
public enum Currency {
    PEN("PEN", "S/"),
    USD("USD", "$");

    private final String code;
    private final String symbol;

    Currency(String code, String symbol) {
        this.code = code;
        this.symbol = symbol;
    }

    public String getCode() {
        return code;
    }

    public String getSymbol() {
        return symbol;
    }

    // --- Backward compatibility methods ---

    public String getCodigo() {
        return code;
    }

    public String getSimbolo() {
        return symbol;
    }
}
