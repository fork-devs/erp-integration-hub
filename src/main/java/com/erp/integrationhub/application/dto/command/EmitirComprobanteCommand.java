package com.erp.integrationhub.application.dto.command;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record EmitirComprobanteCommand(
        UUID tenantId,
        UUID comprobanteId,
        String tipo, // FACTURA, BOLETA, NOTA_CREDITO
        String serie,
        int numero,
        String clienteTipoDoc,
        String clienteNumDoc,
        String clienteDenominacion,
        String clienteDireccion,
        String clienteEmail,
        String moneda,
        BigDecimal totalGravada,
        BigDecimal totalIgv,
        BigDecimal total,
        List<ItemComprobanteCommand> items,
        LocalDate fechaEmision,
        String idempotencyKey
) {}
