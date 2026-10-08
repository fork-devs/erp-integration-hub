package com.erp.integrationhub.interfaces.rest.response;

import com.erp.integrationhub.application.dto.result.PagoResult;
import java.util.UUID;

public record PagoResponse(
        UUID paymentId,
        boolean exitoso,
        String estado,
        String chargeId,
        String clientSecret,
        String codigoAutorizacion,
        String mensajeError,
        boolean cachedResponse
) {
    public static PagoResponse fromResult(PagoResult result) {
        return new PagoResponse(
                result.paymentId(),
                result.exitoso(),
                result.estado(),
                result.chargeId(),
                result.clientSecret(),
                result.codigoAutorizacion(),
                result.mensajeError(),
                result.cachedResponse()
        );
    }
}
