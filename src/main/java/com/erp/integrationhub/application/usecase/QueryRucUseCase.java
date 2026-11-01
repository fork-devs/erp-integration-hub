package com.erp.integrationhub.application.usecase;

import com.erp.integrationhub.application.dto.command.QueryRucCommand;
import com.erp.integrationhub.application.dto.result.RucResult;

/**
 * Use case for querying taxpayer information by RUC.
 */
public interface QueryRucUseCase {

    /**
     * Queries taxpayer details for the given RUC.
     *
     * @param command query RUC command
     * @return taxpayer query result
     */
    RucResult queryRuc(QueryRucCommand command);

    // --- Backward compatibility default method ---

    default RucResult consultarRuc(QueryRucCommand command) {
        return queryRuc(command);
    }
}
