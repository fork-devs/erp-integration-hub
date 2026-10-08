package com.erp.integrationhub.application.usecase;

import com.erp.integrationhub.application.dto.command.EmitirComprobanteCommand;
import com.erp.integrationhub.application.dto.result.ComprobanteResult;

public interface EmitirComprobanteUseCase {
    ComprobanteResult emitirComprobante(EmitirComprobanteCommand command);
}
