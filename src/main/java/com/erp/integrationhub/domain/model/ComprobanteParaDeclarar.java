package com.erp.integrationhub.domain.model;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public class ComprobanteParaDeclarar implements Serializable {
    private final UUID tenantId;
    private final UUID comprobanteId;
    private final ComprobanteTipo tipo;
    private final String serie; // ej. F001 o B001
    private final int numero;   // ej. 124
    private final String clienteTipoDoc; // 6: RUC, 1: DNI, etc.
    private final String clienteNumDoc;
    private final String clienteDenominacion;
    private final String clienteDireccion;
    private final String clienteEmail;
    private final Moneda moneda;
    private final BigDecimal totalGravada;
    private final BigDecimal totalIgv;
    private final BigDecimal total;
    private final List<ItemComprobante> items;
    private final LocalDate fechaEmision;

    public ComprobanteParaDeclarar(
            UUID tenantId,
            UUID comprobanteId,
            ComprobanteTipo tipo,
            String serie,
            int numero,
            String clienteTipoDoc,
            String clienteNumDoc,
            String clienteDenominacion,
            String clienteDireccion,
            String clienteEmail,
            Moneda moneda,
            BigDecimal totalGravada,
            BigDecimal totalIgv,
            BigDecimal total,
            List<ItemComprobante> items,
            LocalDate fechaEmision) {
        if (tenantId == null || comprobanteId == null || tipo == null || serie == null || items == null || items.isEmpty()) {
            throw new IllegalArgumentException("Campos obligatorios del comprobante no pueden ser nulos o vacíos");
        }
        this.tenantId = tenantId;
        this.comprobanteId = comprobanteId;
        this.tipo = tipo;
        this.serie = serie.toUpperCase().trim();
        this.numero = numero;
        this.clienteTipoDoc = clienteTipoDoc != null ? clienteTipoDoc : (tipo == ComprobanteTipo.FACTURA ? "6" : "1");
        this.clienteNumDoc = clienteNumDoc != null ? clienteNumDoc.trim() : "";
        this.clienteDenominacion = clienteDenominacion != null ? clienteDenominacion.trim() : "CLIENTES VARIOS";
        this.clienteDireccion = clienteDireccion != null ? clienteDireccion.trim() : "";
        this.clienteEmail = clienteEmail != null ? clienteEmail.trim() : "";
        this.moneda = moneda != null ? moneda : Moneda.PEN;
        this.totalGravada = totalGravada != null ? totalGravada : BigDecimal.ZERO;
        this.totalIgv = totalIgv != null ? totalIgv : BigDecimal.ZERO;
        this.total = total != null ? total : BigDecimal.ZERO;
        this.items = List.copyOf(items);
        this.fechaEmision = fechaEmision != null ? fechaEmision : LocalDate.now();
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public UUID getComprobanteId() {
        return comprobanteId;
    }

    public ComprobanteTipo getTipo() {
        return tipo;
    }

    public String getSerie() {
        return serie;
    }

    public int getNumero() {
        return numero;
    }

    public String getClienteTipoDoc() {
        return clienteTipoDoc;
    }

    public String getClienteNumDoc() {
        return clienteNumDoc;
    }

    public String getClienteDenominacion() {
        return clienteDenominacion;
    }

    public String getClienteDireccion() {
        return clienteDireccion;
    }

    public String getClienteEmail() {
        return clienteEmail;
    }

    public Moneda getMoneda() {
        return moneda;
    }

    public BigDecimal getTotalGravada() {
        return totalGravada;
    }

    public BigDecimal getTotalIgv() {
        return totalIgv;
    }

    public BigDecimal getTotal() {
        return total;
    }

    public List<ItemComprobante> getItems() {
        return items;
    }

    public LocalDate getFechaEmision() {
        return fechaEmision;
    }
}
