package com.erp.integrationhub.application.dto.result;

import java.util.UUID;

public record ComprobanteResult(
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
) {}
