-- =============================================================================
-- ERP INTEGRATION HUB - V4: HUELLA DE LA CARGA UTIL, ESTADO DE LA CLAVE Y
-- AVISOS PROCESADOS
-- =============================================================================
-- La tabla de idempotencia guardaba la respuesta, pero no la HUELLA de lo que
-- se pidio. Con esa informacion ausente, la misma clave de idempotencia
-- servia para reproducir una peticion DISTINTA: el sistema devolvia el
-- resultado de la primera, y el cobro de la segunda no se hacia.
--
-- La huella de la carga util es criptografica: con una huella de 32 bits dos
-- cargas distintas la comparten con una probabilidad alta, y la comprobacion
-- daria un falso "es la misma peticion".
--
-- El ESTADO de la clave distingue una operacion EN CURSO de una COMPLETADA.
-- Sin el, un fallo transitorio dejaba la clave con la respuesta del fallo,
-- y la siguiente peticion con la misma clave recibia ese fallo como
-- resultado definitivo: la operacion se perdia.
--
-- La tabla de avisos procesados registra que aviso se han aplicado ya. Sin
-- ella, la reentrega de un aviso aplicaba su efecto otra vez.
--
-- Ver docs/doc-estado-implementacion.md, hallazgos P0 5.9 y P1 6.3.
-- =============================================================================

ALTER TABLE idempotency_keys
    ADD COLUMN IF NOT EXISTS request_hash VARCHAR(64),
    ADD COLUMN IF NOT EXISTS state VARCHAR(20) NOT NULL DEFAULT 'COMPLETED';

COMMENT ON COLUMN idempotency_keys.request_hash IS
    'Huella SHA-256 de la carga util de la peticion. Distingue la misma peticion de otra con la misma clave.';

COMMENT ON COLUMN idempotency_keys.state IS
    'Estado de la clave: IN_PROGRESS, COMPLETED, RELEASED o REJECTED.';

CREATE TABLE IF NOT EXISTS processed_webhooks (
    event_id     UUID PRIMARY KEY,
    tenant_id    UUID NOT NULL,
    provider     VARCHAR(50) NOT NULL,
    event_type   VARCHAR(100),
    received_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

-- La consulta de duplicados es por identificador de evento, que es la clave
-- primaria; este indice sirve para las consultas por taller.
CREATE INDEX IF NOT EXISTS idx_processed_webhooks_tenant ON processed_webhooks(tenant_id, received_at);

COMMENT ON TABLE processed_webhooks IS
    'Avisos ya procesados. La clave unica es el identificador de evento del aviso.';

-- El rol de ejecucion necesita consultar y escribir estas filas.
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'erp_app') THEN
        EXECUTE 'GRANT SELECT, INSERT, DELETE ON TABLE processed_webhooks TO erp_app';
        EXECUTE 'GRANT SELECT, INSERT, UPDATE ON TABLE idempotency_keys TO erp_app';
    END IF;
END
$$;
