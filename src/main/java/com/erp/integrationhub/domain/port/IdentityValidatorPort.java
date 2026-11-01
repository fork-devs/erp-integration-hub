package com.erp.integrationhub.domain.port;

import com.erp.integrationhub.domain.model.QueryRucResult;

/**
 * Port outbound to external tax identity validation provider (SUNAT / Chequea.pe).
 */
public interface IdentityValidatorPort {

    /**
     * Queries taxpayer data by 11-digit RUC.
     *
     * @param ruc taxpayer identification number
     * @return taxpayer details and fiscal status
     */
    QueryRucResult queryRuc(String ruc);

    // --- Backward compatibility default method ---

    default QueryRucResult consultarRuc(String ruc) {
        return queryRuc(ruc);
    }
}
