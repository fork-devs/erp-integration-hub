package com.erp.integrationhub.application.dto.command;

import java.math.BigDecimal;
import java.util.UUID;

public record ProcesarPagoCommand(
        UUID tenantId,
        UUID paymentId,
        BigDecimal monto,
        String moneda,
        String tokenTarjeta,
        String descripcion,
        String clienteEmail,
        String idempotencyKey
) {}
