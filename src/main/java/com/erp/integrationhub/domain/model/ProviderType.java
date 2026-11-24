package com.erp.integrationhub.domain.model;

public enum ProviderType {
    /** Comprobantes fiscales (SUNAT, a traves de APISUNAT). */
    APISUNAT,
    /** Pasarela de pagos (Mercado Pago). */
    MERCADOPAGO,
    /** Padron fiscal de RUC (Chequea.pe). */
    CHEQUEA,
    /** Padron vehicular (SUNARP). */
    SUNARP,
    /** Identidad personal DNI / CE (Factiliza / RENIEC / Migraciones). */
    FACTILIZA
}
