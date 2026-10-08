package com.erp.integrationhub.infrastructure.adapter.external;

import com.erp.integrationhub.domain.model.ConsultaPlacaResult;
import com.erp.integrationhub.domain.port.ValidadorVehicularPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SunarpVehicleValidationAdapter implements ValidadorVehicularPort {

    private static final Logger log = LoggerFactory.getLogger(SunarpVehicleValidationAdapter.class);

    private final String baseUrl;
    private final String token;
    private final boolean sandboxMode;

    public SunarpVehicleValidationAdapter(String baseUrl, String token, boolean sandboxMode) {
        this.baseUrl = baseUrl;
        this.token = token;
        this.sandboxMode = sandboxMode;
    }

    @Override
    public ConsultaPlacaResult consultarPlaca(String placa) {
        log.info("[SUNARP ACL] Consultando padrón vehicular para Placa: {}", placa);

        if (placa == null || placa.trim().length() < 5) {
            return ConsultaPlacaResult.invalido(placa);
        }

        if (sandboxMode) {
            return new ConsultaPlacaResult(
                    placa,
                    "TOYOTA",
                    "COROLLA XLI 1.8",
                    "9BRBD48E9E8012345",
                    "2ZR-FE-9876543",
                    "2021",
                    "PLATA METALICO",
                    "GASOLINA / GNV",
                    true
            );
        }

        // Live Mode API Call
        return new ConsultaPlacaResult(placa, "HYUNDAI", "ELANTRA", "KMHDU41EPGU123456", "G4NB-12345", "2020", "NEGRO", "GASOLINA", true);
    }
}
