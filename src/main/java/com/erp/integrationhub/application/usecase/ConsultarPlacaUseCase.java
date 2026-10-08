package com.erp.integrationhub.application.usecase;

import com.erp.integrationhub.application.dto.command.ConsultarPlacaCommand;
import com.erp.integrationhub.application.dto.result.PlacaResult;

public interface ConsultarPlacaUseCase {
    PlacaResult consultarPlaca(ConsultarPlacaCommand command);
}
