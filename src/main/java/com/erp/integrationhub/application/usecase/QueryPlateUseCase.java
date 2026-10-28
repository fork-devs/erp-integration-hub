package com.erp.integrationhub.application.usecase;

import com.erp.integrationhub.application.dto.command.QueryPlateCommand;
import com.erp.integrationhub.application.dto.result.PlateResult;

/**
 * Use case for querying vehicle registration by license plate.
 */
public interface QueryPlateUseCase {

    /**
     * Queries vehicle information for the given plate.
     *
     * @param command query plate command
     * @return vehicle plate query result
     */
    PlateResult queryPlate(QueryPlateCommand command);

    // --- Backward compatibility default method ---

    default PlateResult consultarPlaca(QueryPlateCommand command) {
        return queryPlate(command);
    }
}
