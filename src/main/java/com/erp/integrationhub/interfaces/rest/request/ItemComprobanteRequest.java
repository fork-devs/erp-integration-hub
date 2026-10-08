package com.erp.integrationhub.interfaces.rest.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record ItemComprobanteRequest(
        @NotBlank(message = "La unidad de medida es requerida (ej. NIU, ZZ)")
        String unidadMedida,

        @NotBlank(message = "La descripcion del item es requerida")
        String descripcion,

        @NotNull(message = "La cantidad es requerida")
        @DecimalMin(value = "0.01", message = "La cantidad debe ser mayor a 0")
        BigDecimal cantidad,

        @NotNull(message = "El valor unitario es requerido")
        @DecimalMin(value = "0.0", inclusive = true, message = "El valor unitario no puede ser negativo")
        BigDecimal valorUnitario,

        @NotNull(message = "El precio unitario es requerido")
        @DecimalMin(value = "0.0", inclusive = true, message = "El precio unitario no puede ser negativo")
        BigDecimal precioUnitario,

        @NotNull(message = "El IGV es requerido")
        @DecimalMin(value = "0.0", inclusive = true, message = "El IGV no puede ser negativo")
        BigDecimal igv,

        @NotNull(message = "El total es requerido")
        @DecimalMin(value = "0.0", inclusive = true, message = "El total no puede ser negativo")
        BigDecimal total
) {}
