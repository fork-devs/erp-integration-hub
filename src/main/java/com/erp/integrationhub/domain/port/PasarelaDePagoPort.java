package com.erp.integrationhub.domain.port;

import com.erp.integrationhub.domain.model.ResultadoPago;
import com.erp.integrationhub.domain.model.SolicitudCobro;

public interface PasarelaDePagoPort {
    ResultadoPago procesarCobro(SolicitudCobro solicitud);
    ResultadoPago consultarEstadoPago(String chargeId);
}
