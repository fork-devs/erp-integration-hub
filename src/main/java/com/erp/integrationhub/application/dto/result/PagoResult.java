package com.erp.integrationhub.application.dto.result;

import java.util.UUID;

public record PagoResult(
        UUID paymentId,
        boolean exitoso,
        String estado,
        String chargeId,
        String clientSecret,
        String codigoAutorizacion,
        String mensajeError,
        boolean cachedResponse
) {}
