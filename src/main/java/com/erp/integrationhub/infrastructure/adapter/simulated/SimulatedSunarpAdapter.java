package com.erp.integrationhub.infrastructure.adapter.simulated;

import com.erp.integrationhub.domain.model.QueryPlateResult;
import com.erp.integrationhub.domain.port.VehicleValidatorPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Simulated vehicle validator adapter for development and testing profiles.
 */
public class SimulatedSunarpAdapter implements VehicleValidatorPort {

    private static final Logger log = LoggerFactory.getLogger(SimulatedSunarpAdapter.class);

    @Override
    public QueryPlateResult queryPlate(String plate) {
        log.warn("[SIMULATED] Querying simulated plate {}: no upstream contact with SUNARP", plate);

        if (plate == null || plate.trim().length() < 5) {
            return QueryPlateResult.invalid(plate);
        }

        String sanitizedPlate = plate.trim().toUpperCase().replace("-", "");
        return new QueryPlateResult(
                sanitizedPlate,
                "TOYOTA",
                "COROLLA XLI 1.8",
                "9BRBD48E9E8012345",
                "2ZR-FE-9876543",
                "2021",
                "PLATA METALICO",
                "GASOLINA / GNV",
                true);
    }
}
