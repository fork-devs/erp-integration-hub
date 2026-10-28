package com.erp.integrationhub.infrastructure.adapter.simulated;

import com.erp.integrationhub.domain.model.DeclarationResult;
import com.erp.integrationhub.domain.model.InvoiceStatus;
import com.erp.integrationhub.domain.model.InvoiceToDeclare;
import com.erp.integrationhub.domain.port.TaxAuthorityEmitterPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

/**
 * Simulated tax authority adapter for development and testing profiles.
 */
public class SimulatedApiSunatAdapter implements TaxAuthorityEmitterPort {

    private static final Logger log = LoggerFactory.getLogger(SimulatedApiSunatAdapter.class);

    @Override
    public DeclarationResult emitInvoice(InvoiceToDeclare invoice) {
        log.warn("[SIMULATED] Emitting simulated invoice {} {}-{}: no upstream contact with SUNAT",
                invoice.getType(), invoice.getSeries(), invoice.getNumber());

        String hash = "4HY34uR7a+Vc1Ax3/zrUk1Ng+tjTLwlqa9mRFysy15E=";
        String cleanDoc = invoice.getCustomerDocNumber() != null ? invoice.getCustomerDocNumber() : "20123456789";
        String code = invoice.getType().name().startsWith("F") ? "01" : "03";
        String cdrUrl = String.format("https://simulacion.invalid/R-%s-%s-%s-%d.xml", cleanDoc, code,
                invoice.getSeries(), invoice.getNumber());
        String xmlUrl = String.format("https://simulacion.invalid/%s-%s-%s-%d.xml", cleanDoc, code,
                invoice.getSeries(), invoice.getNumber());
        String pdfUrl = String.format("https://simulacion.invalid/pdf/a4/%s-%s-%s-%d", cleanDoc, code,
                invoice.getSeries(), invoice.getNumber());

        return DeclarationResult.accepted(
                "0",
                "SIMULATED voucher: no actual contact with SUNAT.",
                pdfUrl,
                xmlUrl,
                cdrUrl,
                hash);
    }

    @Override
    public DeclarationResult voidInvoice(String type, String series, int number, String reason) {
        log.warn("[SIMULATED] Voiding simulated voucher {}-{}: no upstream contact with SUNAT", series, number);
        return DeclarationResult.withStatus(InvoiceStatus.VOIDED, "0",
                "SIMULATED voucher voided: no actual contact with SUNAT.", null, null, null, null);
    }

    @Override
    public DeclarationResult queryInvoiceStatus(String type, String series, int number) {
        log.warn("[SIMULATED] Querying simulated voucher status {}-{}", series, number);
        return DeclarationResult.accepted("0", "SIMULATED voucher: no actual contact with SUNAT.", null, null, null, null);
    }
}
