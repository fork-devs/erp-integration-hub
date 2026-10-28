package com.erp.integrationhub.domain.port;

import com.erp.integrationhub.domain.model.QueryPlateResult;
import com.erp.integrationhub.domain.model.QueryRucResult;

import java.util.Optional;

/**
 * Port for caching external identity queries (RUC and vehicle plate).
 */
public interface IdentityCachePort {

    /**
     * Retrieves cached taxpayer information for the given RUC.
     *
     * @param ruc taxpayer registration number
     * @return cached result if present
     */
    Optional<QueryRucResult> getRuc(String ruc);

    /**
     * Stores taxpayer query result in cache.
     *
     * @param ruc taxpayer registration number
     * @param result query result to cache
     */
    void putRuc(String ruc, QueryRucResult result);

    /**
     * Retrieves cached vehicle information for the given license plate.
     *
     * @param plate vehicle license plate
     * @return cached result if present
     */
    Optional<QueryPlateResult> getPlaca(String plate);

    /**
     * Stores vehicle query result in cache.
     *
     * @param plate vehicle license plate
     * @param result query result to cache
     */
    void putPlaca(String plate, QueryPlateResult result);

    // --- Backward compatibility default methods ---

    default Optional<QueryPlateResult> getPlate(String plate) {
        return getPlaca(plate);
    }

    default void putPlate(String plate, QueryPlateResult result) {
        putPlaca(plate, result);
    }
}
