package com.erp.integrationhub.interfaces.rest.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.UUID;

public record ProcesarPagoRequest(
        UUID paymentId,

        @NotNull(message = "El monto es requerido")
        @DecimalMin(value = "0.50", message = "El monto minimo a cobrar es 0.50")
        BigDecimal monto,

        String moneda,

        @NotBlank(message = "El token de tarjeta o payment method es requerido")
        String tokenTarjeta,

        @NotBlank(message = "La descripcion del cobro es requerida")
        String descripcion,

        String clienteEmail
) {}
