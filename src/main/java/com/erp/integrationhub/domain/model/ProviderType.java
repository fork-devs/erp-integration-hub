package com.erp.integrationhub.domain.model;

public enum ProviderType {
    /** Comprobantes fiscales (SUNAT, a traves de APISUNAT). */
    APISUNAT,
    /** Pagos con tarjeta (Stripe). */
    STRIPE,
    /** Padron fiscal de RUC (Chequea.pe). */
    CHEQUEA,
    /** Padron vehicular (SUNARP). */
    SUNARP
}
