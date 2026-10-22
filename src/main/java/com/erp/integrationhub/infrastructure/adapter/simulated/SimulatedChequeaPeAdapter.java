package com.erp.integrationhub.infrastructure.adapter.simulated;

import com.erp.integrationhub.domain.model.ConsultaRucResult;
import com.erp.integrationhub.domain.port.ValidadorIdentidadPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Validador de RUC SIMULADO.
 *
 * <p>Responde con una razon social y un domicilio que no son los del titular
 * real. Solo puede registrarse en un perfil de desarrollo: ver
 * {@code ProviderAdapterRegistry}.
 */
public class SimulatedChequeaPeAdapter implements ValidadorIdentidadPort {

    private static final Logger log = LoggerFactory.getLogger(SimulatedChequeaPeAdapter.class);

    @Override
    public ConsultaRucResult consultarRuc(String ruc) {
        log.warn("[SIMULADO] Se consulta un RUC SIMULADO {}: no se contacta con Chequea.pe", ruc);

        if (ruc == null || !ruc.matches("^(10|20|15|17)\\d{9}$")) {
            return ConsultaRucResult.invalido(ruc);
        }

        String razonSocial = ruc.startsWith("20") ? "SERVICIOS AUTOMOTRICES LIMA S.A.C." : "JUAN CARLOS PEREZ GOMEZ";
        return new ConsultaRucResult(
                ruc,
                razonSocial,
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
