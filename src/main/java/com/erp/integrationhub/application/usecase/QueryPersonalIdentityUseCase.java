package com.erp.integrationhub.application.usecase;

import com.erp.integrationhub.application.dto.result.PersonalIdentityResult;

import java.util.UUID;

/**
 * Use case for querying personal identity information (DNI, CE) via official registries.
 */
public interface QueryPersonalIdentityUseCase {

    /**
     * Queries individual identity by document type and document number.
     *
     * @param tenantId workshop tenant identifier
     * @param documentType document type (e.g., "DNI", "CE")
     * @param documentNumber document number
     * @return personal identity query result
     */
    PersonalIdentityResult queryPersonalIdentity(UUID tenantId, String documentType, String documentNumber);
}
