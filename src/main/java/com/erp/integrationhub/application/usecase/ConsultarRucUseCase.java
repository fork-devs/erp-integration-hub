package com.erp.integrationhub.application.usecase;

import com.erp.integrationhub.application.dto.command.ConsultarRucCommand;
import com.erp.integrationhub.application.dto.result.RucResult;

public interface ConsultarRucUseCase {
    RucResult consultarRuc(ConsultarRucCommand command);
}
