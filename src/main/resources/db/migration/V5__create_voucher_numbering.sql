-- =============================================================================
-- ERP INTEGRATION HUB - V5: NUMERACION DE COMPROBANTES
-- =============================================================================
-- La numeracion (taller, serie, numero) es la identidad FISCAL de un
-- comprobante. Si dos comprobantes del mismo taller comparten serie y numero,
-- la autoridad fiscal ve un duplicado, y el sistema tiene dos comprobantes
-- con el mismo identificador ante el cliente.
--
-- La unicidad se comprueba ANTES de contactar al proveedor, y la reserva de
-- la numeracion es lo que la hace efectiva: si se comprueba despues, dos
-- emisiones simultaneas con la misma numeracion pasan las dos y el
-- duplicado ya esta en SUNAT.
--
-- Ver docs/doc-estado-implementacion.md, hallazgo P1 6.43.
-- =============================================================================

CREATE TABLE IF NOT EXISTS voucher_numberings (
    tenant_id      UUID NOT NULL,
    serie          VARCHAR(10) NOT NULL,
    numero         INTEGER NOT NULL,
    comprobante_id UUID NOT NULL,
    created_at     TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    PRIMARY KEY (tenant_id, serie, numero)
);

CREATE INDEX IF NOT EXISTS idx_voucher_numberings_document ON voucher_numberings(comprobante_id);

COMMENT ON TABLE voucher_numberings IS
    'Numeracion reservada de comprobantes. La clave primaria es la que impide emitir dos comprobantes con la misma numeracion en un taller.';

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'erp_app') THEN
        EXECUTE 'GRANT SELECT, INSERT, DELETE ON TABLE voucher_numberings TO erp_app';
    END IF;
END
$$;
