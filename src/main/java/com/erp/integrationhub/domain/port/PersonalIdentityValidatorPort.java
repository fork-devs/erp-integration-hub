package com.erp.integrationhub.domain.port;

import com.erp.integrationhub.domain.model.QueryPersonalIdentityResult;

/**
 * Port outbound to external personal identity validation provider (RENIEC / Migraciones via Factiliza).
 */
public interface PersonalIdentityValidatorPort {

    /**
     * Queries personal identity data by 8-digit Peruvian National Identity Document (DNI).
     *
     * @param dni 8-digit DNI number
     * @return personal identity details
     */
    QueryPersonalIdentityResult queryDni(String dni);

    /**
     * Queries foreign personal identity data by Carnet de Extranjería (CE).
     *
     * @param cee CE document number
     * @return foreign personal identity details
     */
    QueryPersonalIdentityResult queryCee(String cee);
}
