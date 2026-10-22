package com.erp.integrationhub.infrastructure.adapter.simulated;

import com.erp.integrationhub.domain.model.ComprobanteParaDeclarar;
import com.erp.integrationhub.domain.model.ResultadoDeclaracion;
import com.erp.integrationhub.domain.port.EmisorTributarioPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

/**
 * Emisor tributario SIMULADO.
 *
 * <p>Fabrica respuestas plausibles, y las marca como tales en el log. Solo
 * puede registrarse en un perfil de desarrollo: ver
 * {@code ProviderAdapterRegistry}, que hace fallar el arranque si un
 * adaptador simulado se registra fuera de ese perfil.
 *
 * <p>La respuesta simulada dice "ACEPTADO" con una URL de PDF que no
 * existe. En un despliegue real, eso es un comprobante fantasma en la
 * bitacora de auditoria.
 */
public class SimulatedApiSunatAdapter implements EmisorTributarioPort {

    private static final Logger log = LoggerFactory.getLogger(SimulatedApiSunatAdapter.class);

    @Override
    public ResultadoDeclaracion emitirComprobante(ComprobanteParaDeclarar comprobante) {
        log.warn("[SIMULADO] Se emite un comprobante SIMULADO {} {}-{}: no se contacta con SUNAT",
                comprobante.getTipo(), comprobante.getSerie(), comprobante.getNumero());

        String hash = "4HY34uR7a+Vc1Ax3/zrUk1Ng+tjTLwlqa9mRFysy15E=";
        String cleanDoc = comprobante.getClienteNumDoc() != null ? comprobante.getClienteNumDoc() : "20123456789";
        String code = comprobante.getTipo().name().startsWith("F") ? "01" : "03";
        String cdrUrl = String.format("https://simulacion.invalid/R-%s-%s-%s-%d.xml", cleanDoc, code,
                comprobante.getSerie(), comprobante.getNumero());
        String xmlUrl = String.format("https://simulacion.invalid/%s-%s-%s-%d.xml", cleanDoc, code,
                comprobante.getSerie(), comprobante.getNumero());
        String pdfUrl = String.format("https://simulacion.invalid/pdf/a4/%s-%s-%s-%d", cleanDoc, code,
                comprobante.getSerie(), comprobante.getNumero());

        return ResultadoDeclaracion.aceptado(
                "0",
                "Comprobante SIMULADO: no se contacto con SUNAT.",
                pdfUrl,
                xmlUrl,
                cdrUrl,
                hash);
    }

    @Override
    public ResultadoDeclaracion anularComprobante(String tipo, String serie, int numero, String motivo) {
        log.warn("[SIMULADO] Se anula un comprobante SIMULADO {}-{}: no se contacta con SUNAT", serie, numero);
        return ResultadoDeclaracion.anulado("0",
                "Comprobante SIMULADO: no se contacto con SUNAT.",
                "BAJA-" + UUID.randomUUID().toString().substring(0, 8));
    }

    @Override
    public ResultadoDeclaracion consultarEstadoComprobante(String tipo, String serie, int numero) {
        log.warn("[SIMULADO] Se consulta el estado de un comprobante SIMULADO {}-{}", serie, numero);
        return ResultadoDeclaracion.aceptado("0", "Comprobante SIMULADO: no se contacto con SUNAT.", null, null, null, null);
    }
}
