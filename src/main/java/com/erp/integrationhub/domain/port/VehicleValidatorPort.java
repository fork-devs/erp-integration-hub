package com.erp.integrationhub.domain.port;

import com.erp.integrationhub.domain.model.QueryPlateResult;

/**
 * Port outbound to external vehicle registry provider (SUNARP).
 */
public interface VehicleValidatorPort {

    /**
     * Queries vehicle technical specifications and registration status by license plate.
     *
     * @param plate vehicle license plate
     * @return vehicle data including VIN, engine, brand, model
     */
    QueryPlateResult queryPlate(String plate);

    // --- Backward compatibility default method ---

    default QueryPlateResult consultarPlaca(String placa) {
        return queryPlate(placa);
    }
}
