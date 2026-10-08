package com.erp.integrationhub.interfaces.rest.response;

import com.erp.integrationhub.application.dto.result.ComprobanteResult;
import java.util.UUID;

public record ComprobanteResponse(
        UUID comprobanteId,
        boolean exitoso,
        String estadoSunat,
        String codigoRespuestaSunat,
        String mensajeSunat,
        String pdfUrl,
        String xmlUrl,
        String cdrUrl,
        String hashCdr,
        boolean cachedResponse
) {
    public static ComprobanteResponse fromResult(ComprobanteResult result) {
        return new ComprobanteResponse(
                result.comprobanteId(),
                result.exitoso(),
                result.estadoSunat(),
                result.codigoRespuestaSunat(),
                result.mensajeSunat(),
                result.pdfUrl(),
                result.xmlUrl(),
                result.cdrUrl(),
                result.hashCdr(),
                result.cachedResponse()
        );
    }
}
