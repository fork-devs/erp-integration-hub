package com.erp.integrationhub.application.dto.command;

import java.math.BigDecimal;

public record ItemComprobanteCommand(
        String unidadMedida,
        String descripcion,
        BigDecimal cantidad,
        BigDecimal valorUnitario,
        BigDecimal precioUnitario,
        BigDecimal igv,
        BigDecimal total
) {}
