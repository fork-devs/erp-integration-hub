package com.erp.integrationhub.domain.exception;

/**
 * La numeracion del comprobante ya esta reservada en ese taller.
 *
 * <p>Emitir un comprobante con la misma serie y numero de otro produce un
 * duplicado ante la autoridad fiscal.
 */
public class VoucherNumberingConflictException extends DomainException {

    public VoucherNumberingConflictException(String serie, int numero) {
        super("La numeracion " + serie + "-" + numero + " ya fue emitida en este taller", "VOUCHER_NUMBERING_CONFLICT");
    }
}
