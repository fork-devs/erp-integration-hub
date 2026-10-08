package com.erp.integrationhub.domain.port;

import com.erp.integrationhub.domain.model.ConsultaPlacaResult;

public interface ValidadorVehicularPort {
    ConsultaPlacaResult consultarPlaca(String placa);
}
