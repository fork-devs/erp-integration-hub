package com.erp.integrationhub.application.usecase;

import com.erp.integrationhub.application.dto.command.EmitirComprobanteCommand;
import com.erp.integrationhub.application.dto.result.ComprobanteResult;

import java.util.UUID;

public interface EmitirComprobanteUseCase {
    ComprobanteResult emitirComprobante(EmitirComprobanteCommand command);
    ComprobanteResult anularComprobante(UUID tenantId, UUID comprobanteId, String tipo, String serie, int numero, String motivo);
    ComprobanteResult consultarEstadoComprobante(UUID tenantId, UUID comprobanteId, String tipo, String serie, int numero);
}
