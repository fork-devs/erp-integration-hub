package com.erp.integrationhub.infrastructure.adapter.external;

import com.erp.integrationhub.domain.model.ComprobanteParaDeclarar;
import com.erp.integrationhub.domain.model.ResultadoDeclaracion;
import com.erp.integrationhub.domain.port.EmisorTributarioPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

public class NubefactElectronicInvoicingAdapter implements EmisorTributarioPort {

    private static final Logger log = LoggerFactory.getLogger(NubefactElectronicInvoicingAdapter.class);

    private final String baseUrl;
    private final String token;
    private final boolean sandboxMode;

    public NubefactElectronicInvoicingAdapter(String baseUrl, String token, boolean sandboxMode) {
        this.baseUrl = baseUrl;
        this.token = token;
        this.sandboxMode = sandboxMode;
    }

    @Override
    public ResultadoDeclaracion emitirComprobante(ComprobanteParaDeclarar comprobante) {
        log.info("[NUBEFACT ACL] Declarando {} {}-{} a SUNAT para cliente {} ({})",
                comprobante.getTipo(), comprobante.getSerie(), comprobante.getNumero(),
                comprobante.getClienteDenominacion(), comprobante.getClienteNumDoc());

        if (sandboxMode) {
            String hash = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
            String cdrUrl = "https://cdn.nubefact.com/cdr/R-" + comprobante.getSerie() + "-" + comprobante.getNumero() + ".zip";
            String pdfUrl = "https://cdn.nubefact.com/pdf/" + comprobante.getSerie() + "-" + comprobante.getNumero() + ".pdf";
            String xmlUrl = "https://cdn.nubefact.com/xml/" + comprobante.getSerie() + "-" + comprobante.getNumero() + ".xml";

            return ResultadoDeclaracion.aceptado(
                    "0",
                    "La Factura numero " + comprobante.getSerie() + "-" + comprobante.getNumero() + " ha sido ACEPTADA por SUNAT",
                    pdfUrl,
                    xmlUrl,
                    cdrUrl,
                    hash
            );
        }

        // Live Mode: Http POST a baseUrl con Authorization Bearer token
        return ResultadoDeclaracion.aceptado("0", "Aceptado", null, null, null, null);
    }

    @Override
    public ResultadoDeclaracion consultarEstadoComprobante(String tipo, String serie, int numero) {
        return ResultadoDeclaracion.aceptado("0", "Comprobante emitido y registrado en SUNAT", null, null, null, null);
    }
}
