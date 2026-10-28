package com.erp.integrationhub.domain.port;

import com.erp.integrationhub.domain.model.DeclarationResult;
import com.erp.integrationhub.domain.model.InvoiceToDeclare;

/**
 * Port outbound to electronic billing provider / tax authority (APISUNAT / SUNAT).
 */
public interface TaxAuthorityEmitterPort {

    /**
     * Declares an electronic invoice or voucher to SUNAT.
     *
     * @param invoice invoice entity with line items and customer details
     * @return declaration result including CDR and document URLs
     */
    DeclarationResult emitInvoice(InvoiceToDeclare invoice);

    /**
     * Voids an electronic invoice or submits a daily summary for boletas.
     *
     * @param type document type (FACTURA, BOLETA)
     * @param series document series
     * @param number document number
     * @param reason voiding motive
     * @return result of the voiding communication
     */
    DeclarationResult voidInvoice(String type, String series, int number, String reason);

    /**
     * Queries the status of an electronic document in SUNAT.
     *
     * @param type document type
     * @param series document series
     * @param number document number
     * @return current status and validation links
     */
    DeclarationResult queryInvoiceStatus(String type, String series, int number);

    // --- Backward compatibility default methods ---

    default DeclarationResult emitirComprobante(InvoiceToDeclare comprobante) {
        return emitInvoice(comprobante);
    }

    default DeclarationResult anularComprobante(String tipo, String serie, int numero, String motivo) {
        return voidInvoice(tipo, serie, numero, motivo);
    }

    default DeclarationResult consultarEstadoComprobante(String tipo, String serie, int numero) {
        return queryInvoiceStatus(tipo, serie, numero);
    }
}
