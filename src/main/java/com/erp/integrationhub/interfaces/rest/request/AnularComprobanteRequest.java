package com.erp.integrationhub.interfaces.rest.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record AnularComprobanteRequest(
        UUID comprobanteId,

        @NotBlank(message = "El tipo de documento es requerido (factura, boleta, nota_credito, nota_debito)")
        String tipo,

        @NotBlank(message = "La serie es requerida (ej. F001, B001)")
        String serie,

        @NotNull(message = "El numero correlativo es requerido")
        Integer numero,

        @NotBlank(message = "El motivo de anulación es requerido")
        String motivo
) {}
