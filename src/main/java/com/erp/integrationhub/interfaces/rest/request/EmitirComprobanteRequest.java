package com.erp.integrationhub.interfaces.rest.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record EmitirComprobanteRequest(
        UUID comprobanteId,

        @NotBlank(message = "El tipo de comprobante es requerido (FACTURA, BOLETA, NOTA_CREDITO)")
        String tipo,

        @NotBlank(message = "La serie es requerida (ej. F001, B001)")
        String serie,

        @NotNull(message = "El numero correlativo es requerido")
        Integer numero,

        @NotBlank(message = "El tipo de documento del cliente es requerido (6=RUC, 1=DNI)")
        String clienteTipoDoc,

        @NotBlank(message = "El numero de documento del cliente es requerido")
        String clienteNumDoc,

        @NotBlank(message = "La razon social / denominacion del cliente es requerida")
        String clienteDenominacion,

        String clienteDireccion,
        String clienteEmail,

        String moneda,

        @NotNull(message = "El total gravado es requerido")
        @DecimalMin(value = "0.0", inclusive = true, message = "El total gravado no puede ser negativo")
        BigDecimal totalGravada,

        @NotNull(message = "El total IGV es requerido")
        @DecimalMin(value = "0.0", inclusive = true, message = "El total IGV no puede ser negativo")
        BigDecimal totalIgv,

        @NotNull(message = "El total es requerido")
        @DecimalMin(value = "0.01", message = "El total debe ser mayor a 0")
        BigDecimal total,

        @NotEmpty(message = "El comprobante debe tener al menos un item")
        @Valid
        List<ItemComprobanteRequest> items,

        LocalDate fechaEmision
) {}
