-- =============================================================================
-- ERP INTEGRATION HUB - V3: ROW-LEVEL SECURITY POR TALLER
-- =============================================================================
-- Las tres tablas de negocio del hub tienen columna tenant_id, pero ninguna
-- declaraba politica de aislamiento: el aislamiento dependia por completo de
-- que cada consulta incluyera el filtro. Una consulta sin el filtro devolvia
-- datos de todos los talleres. De hecho, el listado de intentos por estado
-- era una consulta transversal.
--
-- La politica es FAIL-CLOSED: si el contexto de taller no esta establecido en
-- la sesion, la tabla devuelve cero filas.
--
-- FORCE ROW LEVEL SECURITY no habilita la RLS: solo obliga a que la politica
-- se aplique tambien al propietario de la tabla. Habilitarla y forzarla son
-- dos sentencias distintas, y hacen falta las dos.
--
-- Se crea ademas un rol de PLATAFORMA con BYPASSRLS, usado UNICAMENTE por el
-- relay de reintentos, que recorre la cola de pendientes de todos los
-- talleres. Es un proceso interno de la plataforma, no una operacion de un
-- taller, y no puede filtrar por taller porque todavia no lo conoce. Su
-- credencial vive separada de la de la aplicacion, de modo que un fallo de
-- seguridad en la aplicacion no puede escalarla.
--
-- Ver docs/doc-estado-implementacion.md, hallazgos P1 6.53 y 6.55.
-- =============================================================================

-- --- idempotency_keys -------------------------------------------------------

ALTER TABLE idempotency_keys ENABLE ROW LEVEL SECURITY;
ALTER TABLE idempotency_keys FORCE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS idempotency_keys_tenant_isolation_policy ON idempotency_keys;

CREATE POLICY idempotency_keys_tenant_isolation_policy ON idempotency_keys
    FOR ALL
    USING (
        tenant_id = nullif(current_setting('app.tenant_id', true), '')::uuid
    )
    WITH CHECK (
        tenant_id = nullif(current_setting('app.tenant_id', true), '')::uuid
    );

-- --- integration_attempts ---------------------------------------------------

ALTER TABLE integration_attempts ENABLE ROW LEVEL SECURITY;
ALTER TABLE integration_attempts FORCE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS integration_attempts_tenant_isolation_policy ON integration_attempts;

CREATE POLICY integration_attempts_tenant_isolation_policy ON integration_attempts
    FOR ALL
    USING (
        tenant_id = nullif(current_setting('app.tenant_id', true), '')::uuid
    )
    WITH CHECK (
        tenant_id = nullif(current_setting('app.tenant_id', true), '')::uuid
    );

-- --- integration_retry_tasks ------------------------------------------------

ALTER TABLE integration_retry_tasks ENABLE ROW LEVEL SECURITY;
ALTER TABLE integration_retry_tasks FORCE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS integration_retry_tasks_tenant_isolation_policy ON integration_retry_tasks;

CREATE POLICY integration_retry_tasks_tenant_isolation_policy ON integration_retry_tasks
    FOR ALL
    USING (
        tenant_id = nullif(current_setting('app.tenant_id', true), '')::uuid
    )
    WITH CHECK (
        tenant_id = nullif(current_setting('app.tenant_id', true), '')::uuid
    );

-- --- Rol de plataforma ------------------------------------------------------
-- Lo usa EXCLUSIVAMENTE el relay de reintentos para recorrer la cola de
-- pendientes de todos los talleres. No lo usa la aplicacion.

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'erp_platform') THEN
        EXECUTE format(
            'CREATE ROLE erp_platform LOGIN NOINHERIT NOSUPERUSER NOCREATEDB '
            'NOCREATEROLE NOREPLICATION BYPASSRLS PASSWORD %L',
            '${platformRolePassword}'
        );
    END IF;
END
$$;

GRANT USAGE ON SCHEMA public TO erp_platform;
GRANT SELECT ON TABLE integration_retry_tasks TO erp_platform;

-- --- Rol de ejecucion de la aplicacion -------------------------------------
-- La aplicacion deja de conectar con el propietario: el propietario de una
-- tabla elude su politica de Row-Level Security, de modo que con un unico rol
-- el aislamiento seria inerte.

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'erp_app') THEN
        EXECUTE format(
            'CREATE ROLE erp_app LOGIN NOINHERIT NOSUPERUSER NOCREATEDB '
            'NOCREATEROLE NOREPLICATION NOBYPASSRLS PASSWORD %L',
            '${appRolePassword}'
        );
    END IF;
END
$$;

GRANT USAGE ON SCHEMA public TO erp_app;
GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE idempotency_keys TO erp_app;
GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE integration_attempts TO erp_app;
GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE integration_retry_tasks TO erp_app;
GRANT SELECT ON TABLE provider_configs TO erp_app;

-- --- Comprobacion -----------------------------------------------------------

DO $$
DECLARE
    sin_forzar TEXT;
BEGIN
    SELECT string_agg(c.relname, ', ')
      INTO sin_forzar
      FROM pg_class c
      JOIN pg_namespace n ON n.oid = c.relnamespace
     WHERE n.nspname = 'public'
       AND c.relname IN ('idempotency_keys', 'integration_attempts', 'integration_retry_tasks')
       AND NOT c.relforcerowsecurity;

    IF sin_forzar IS NOT NULL THEN
        RAISE EXCEPTION 'Tablas sin FORCE ROW LEVEL SECURITY: %', sin_forzar;
    END IF;
END
$$;
