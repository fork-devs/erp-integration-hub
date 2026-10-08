package com.erp.integrationhub.domain.model;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.UUID;

public class SolicitudCobro implements Serializable {
    private final UUID tenantId;
    private final UUID paymentId;
    private final BigDecimal monto;
    private final Moneda moneda;
    private final String tokenTarjeta; // Stripe PaymentMethod / Token
    private final String descripcion;
    private final String clienteEmail;
    private final String idempotencyKey;

    public SolicitudCobro(
            UUID tenantId,
            UUID paymentId,
            BigDecimal monto,
            Moneda moneda,
            String tokenTarjeta,
            String descripcion,
            String clienteEmail,
            String idempotencyKey) {
        if (tenantId == null || paymentId == null || monto == null || monto.compareTo(BigDecimal.ZERO) <= 0 || idempotencyKey == null) {
            throw new IllegalArgumentException("Campos obligatorios de la solicitud de cobro no pueden ser nulos");
        }
        this.tenantId = tenantId;
        this.paymentId = paymentId;
        this.monto = monto;
        this.moneda = moneda != null ? moneda : Moneda.PEN;
        this.tokenTarjeta = tokenTarjeta != null ? tokenTarjeta.trim() : "";
        this.descripcion = descripcion != null ? descripcion.trim() : "Pago de servicio automotriz";
        this.clienteEmail = clienteEmail != null ? clienteEmail.trim() : "";
        this.idempotencyKey = idempotencyKey.trim();
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public UUID getPaymentId() {
        return paymentId;
    }

    public BigDecimal getMonto() {
        return monto;
    }

    public Moneda getMoneda() {
        return moneda;
    }

    public String getTokenTarjeta() {
        return tokenTarjeta;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public String getClienteEmail() {
        return clienteEmail;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }
}
