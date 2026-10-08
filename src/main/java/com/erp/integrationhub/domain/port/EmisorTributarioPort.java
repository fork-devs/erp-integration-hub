package com.erp.integrationhub.domain.port;

import com.erp.integrationhub.domain.model.ComprobanteParaDeclarar;
import com.erp.integrationhub.domain.model.ResultadoDeclaracion;

public interface EmisorTributarioPort {
    ResultadoDeclaracion emitirComprobante(ComprobanteParaDeclarar comprobante);
    ResultadoDeclaracion anularComprobante(String tipo, String serie, int numero, String motivo);
    ResultadoDeclaracion consultarEstadoComprobante(String tipo, String serie, int numero);
}
