package com.erp.integrationhub.domain.port;

import java.util.UUID;

/**
 * Reserva de la numeracion de comprobantes.
 *
 * <p>La numeracion (taller, serie, numero) es la identidad FISCAL de un
 * comprobante. Reservarla ANTES de contactar al proveedor es lo que impide
 * que dos emisiones simultaneas produzcan un duplicado: si se comprueba
 * despues, el duplicado ya esta en SUNAT.
 */
public interface VoucherNumberingPort {

    /**
     * Reserva la numeracion.
     *
     * @throws com.erp.integrationhub.domain.exception.VoucherNumberingConflictException
     *         si la numeracion ya esta reservada en ese taller
     */
    void reserve(UUID tenantId, String serie, int numero, UUID comprobanteId);

    /**
     * Indica si la numeracion esta reservada.
     */
    boolean isReserved(UUID tenantId, String serie, int numero);
}
