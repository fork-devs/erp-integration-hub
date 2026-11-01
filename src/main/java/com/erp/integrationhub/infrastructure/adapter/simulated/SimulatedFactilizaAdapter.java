package com.erp.integrationhub.infrastructure.adapter.simulated;

import com.erp.integrationhub.domain.model.QueryPersonalIdentityResult;
import com.erp.integrationhub.domain.port.PersonalIdentityValidatorPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Simulated personal identity validator adapter for local development and testing profiles.
 */
public class SimulatedFactilizaAdapter implements PersonalIdentityValidatorPort {

    private static final Logger log = LoggerFactory.getLogger(SimulatedFactilizaAdapter.class);

    @Override
    public QueryPersonalIdentityResult queryDni(String dni) {
        log.warn("[SIMULATED] Querying simulated DNI {}: no upstream contact with Factiliza/RENIEC", dni);

        if (dni == null || !dni.matches("^\\d{8}$")) {
            return QueryPersonalIdentityResult.invalid("DNI", dni);
        }

        return new QueryPersonalIdentityResult(
                "DNI",
                dni,
                "JUAN CARLOS",
                "PEREZ",
                "GOMEZ",
                "PEREZ GOMEZ, JUAN CARLOS",
                "9",
                true
        );
    }

    @Override
    public QueryPersonalIdentityResult queryCee(String cee) {
        log.warn("[SIMULATED] Querying simulated CE {}: no upstream contact with Factiliza/Migraciones", cee);

        if (cee == null || cee.trim().isBlank()) {
            return QueryPersonalIdentityResult.invalid("CE", cee);
        }

        return new QueryPersonalIdentityResult(
                "CE",
                cee.trim(),
                "JOHN EDWARD",
                "DOE",
                "SMITH",
                "DOE SMITH, JOHN EDWARD",
                null,
                true
        );
    }
}
