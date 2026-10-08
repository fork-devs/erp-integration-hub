package com.erp.integrationhub.domain.model;

import java.io.Serializable;
import java.math.BigDecimal;

public record ItemComprobante(
        String unidadMedida, // ej. 'NIU' o 'ZZ' (servicio)
        String descripcion,
        BigDecimal cantidad,
        BigDecimal valorUnitario, // Sin IGV
        BigDecimal precioUnitario, // Con IGV
        BigDecimal igv,
        BigDecimal total
) implements Serializable {
    public ItemComprobante {
        if (descripcion == null || descripcion.trim().isEmpty()) {
            throw new IllegalArgumentException("La descripción del ítem no puede estar vacía");
        }
        if (cantidad == null || cantidad.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("La cantidad debe ser mayor a cero");
        }
        if (unidadMedida == null || unidadMedida.trim().isEmpty()) {
            unidadMedida = "NIU";
        }
    }
}
