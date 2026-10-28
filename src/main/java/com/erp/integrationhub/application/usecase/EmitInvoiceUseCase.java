package com.erp.integrationhub.application.usecase;

import com.erp.integrationhub.application.dto.command.EmitInvoiceCommand;
import com.erp.integrationhub.application.dto.result.InvoiceResult;

import java.util.UUID;

/**
 * Use case for electronic invoicing lifecycle (emission, voiding, status inquiry).
 */
public interface EmitInvoiceUseCase {

    /**
     * Emits an electronic invoice or voucher to SUNAT via PSE/OSE.
     *
     * @param command invoice emission command
     * @return emission result
     */
    InvoiceResult emitInvoice(EmitInvoiceCommand command);

    /**
     * Voids an electronic invoice or submits daily summary for boletas.
     *
     * @param tenantId workshop tenant identifier
     * @param invoiceId unique invoice identifier
     * @param type document type (FACTURA, BOLETA)
     * @param series document series
     * @param number document sequence number
     * @param reason cancellation reason
     * @return voiding communication result
     */
    InvoiceResult voidInvoice(UUID tenantId, UUID invoiceId, String type, String series, int number, String reason);

    /**
     * Queries the status of a voucher with the tax authority.
     *
     * @param tenantId workshop tenant identifier
     * @param invoiceId unique invoice identifier
     * @param type document type
     * @param series document series
     * @param number document sequence number
     * @return current voucher status
     */
    InvoiceResult queryInvoiceStatus(UUID tenantId, UUID invoiceId, String type, String series, int number);

    // --- Backward compatibility default methods ---

    default InvoiceResult emitirComprobante(EmitInvoiceCommand command) {
        return emitInvoice(command);
    }

    default InvoiceResult anularComprobante(UUID tenantId, UUID comprobanteId, String tipo, String serie, int numero, String motivo) {
        return voidInvoice(tenantId, comprobanteId, tipo, serie, numero, motivo);
    }

    default InvoiceResult consultarEstadoComprobante(UUID tenantId, UUID comprobanteId, String tipo, String serie, int numero) {
        return queryInvoiceStatus(tenantId, comprobanteId, tipo, serie, numero);
    }
}
