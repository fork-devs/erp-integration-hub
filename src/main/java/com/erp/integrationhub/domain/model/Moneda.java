package com.erp.integrationhub.domain.model;

public enum Moneda {
    PEN("PEN", "S/"),
    USD("USD", "$");

    private final String codigo;
    private final String simbolo;

    Moneda(String codigo, String simbolo) {
        this.codigo = codigo;
        this.simbolo = simbolo;
    }

    public String getCodigo() {
        return codigo;
    }

    public String getSimbolo() {
        return simbolo;
    }
}
