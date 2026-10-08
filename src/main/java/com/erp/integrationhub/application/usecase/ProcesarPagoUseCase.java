package com.erp.integrationhub.application.usecase;

import com.erp.integrationhub.application.dto.command.ProcesarPagoCommand;
import com.erp.integrationhub.application.dto.result.PagoResult;

public interface ProcesarPagoUseCase {
    PagoResult procesarPago(ProcesarPagoCommand command);
}
