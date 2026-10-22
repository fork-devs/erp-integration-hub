package com.erp.integrationhub.infrastructure.adapter.simulated;

import com.erp.integrationhub.domain.model.ConsultaPlacaResult;
import com.erp.integrationhub.domain.port.ValidadorVehicularPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Validador vehicular SIMULADO.
 *
 * <p>Responde con una marca, un modelo y un numero de motor que no son los
 * del vehiculo real. Solo puede registrarse en un perfil de desarrollo: ver
 * {@code ProviderAdapterRegistry}.
 */
public class SimulatedSunarpAdapter implements ValidadorVehicularPort {

    private static final Logger log = LoggerFactory.getLogger(SimulatedSunarpAdapter.class);

    @Override
    public ConsultaPlacaResult consultarPlaca(String placa) {
        log.warn("[SIMULADO] Se consulta una PLACA SIMULADA {}: no se contacta con SUNARP", placa);

        if (placa == null || placa.trim().length() < 5) {
            return ConsultaPlacaResult.invalido(placa);
        }

        String sanitizedPlaca = placa.trim().toUpperCase().replace("-", "");
        return new ConsultaPlacaResult(
                sanitizedPlaca,
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
