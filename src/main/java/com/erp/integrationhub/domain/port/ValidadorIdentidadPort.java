package com.erp.integrationhub.domain.port;

import com.erp.integrationhub.domain.model.ConsultaRucResult;

public interface ValidadorIdentidadPort {
    ConsultaRucResult consultarRuc(String ruc);
}
