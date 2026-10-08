package com.erp.integrationhub.domain.model;

public enum ComprobanteTipo {
    FACTURA("01"),
    BOLETA("03"),
    NOTA_CREDITO("07"),
    NOTA_DEBITO("08");

    private final String codigoSunat;

    ComprobanteTipo(String codigoSunat) {
        this.codigoSunat = codigoSunat;
    }

    public String getCodigoSunat() {
        return codigoSunat;
    }
}
