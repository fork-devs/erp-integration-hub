package com.erp.integrationhub.infrastructure.adapter.simulated;

import com.erp.integrationhub.domain.model.QueryRucResult;
import com.erp.integrationhub.domain.port.IdentityValidatorPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Simulated RUC identity validator adapter for development and testing profiles.
 */
public class SimulatedChequeaPeAdapter implements IdentityValidatorPort {

    private static final Logger log = LoggerFactory.getLogger(SimulatedChequeaPeAdapter.class);

    @Override
    public QueryRucResult queryRuc(String ruc) {
        log.warn("[SIMULATED] Querying simulated RUC {}: no upstream contact with Chequea.pe", ruc);

        if (ruc == null || !ruc.matches("^(10|20|15|17)\\d{9}$")) {
            return QueryRucResult.invalid(ruc);
        }

        String legalName = ruc.startsWith("20") ? "SERVICIOS AUTOMOTRICES LIMA S.A.C." : "JUAN CARLOS PEREZ GOMEZ";
        return new QueryRucResult(
                ruc,
                legalName,
                "ACTIVO",
                "HABIDO",
                "AV. JAVIER PRADO ESTE 1234, SAN ISIDRO",
                "LIMA",
                "LIMA",
                "SAN ISIDRO",
                "150131",
                true);
    }
}
