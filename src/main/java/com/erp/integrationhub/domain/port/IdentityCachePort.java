package com.erp.integrationhub.domain.port;

import com.erp.integrationhub.domain.model.ConsultaPlacaResult;
import com.erp.integrationhub.domain.model.ConsultaRucResult;

import java.util.Optional;

public interface IdentityCachePort {
    Optional<ConsultaRucResult> getRuc(String ruc);
    void putRuc(String ruc, ConsultaRucResult result);
    Optional<ConsultaPlacaResult> getPlaca(String placa);
    void putPlaca(String placa, ConsultaPlacaResult result);
}
