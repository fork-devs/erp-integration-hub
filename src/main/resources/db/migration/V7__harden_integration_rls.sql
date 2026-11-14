-- =============================================================================
-- ERP INTEGRATION HUB - V7: HARDEN TENANT RLS ON WEBHOOKS & VOUCHER NUMBERINGS
-- =============================================================================
-- Enables fail-closed Row-Level Security on processed_webhooks and
-- voucher_numberings, and grants idempotency_keys cleanup access to erp_platform.
-- =============================================================================

-- --- processed_webhooks ------------------------------------------------------

ALTER TABLE processed_webhooks ENABLE ROW LEVEL SECURITY;
ALTER TABLE processed_webhooks FORCE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS processed_webhooks_tenant_isolation_policy ON processed_webhooks;

CREATE POLICY processed_webhooks_tenant_isolation_policy ON processed_webhooks
    FOR ALL
    USING (
        tenant_id = nullif(current_setting('app.tenant_id', true), '')::uuid
    )
    WITH CHECK (
        tenant_id = nullif(current_setting('app.tenant_id', true), '')::uuid
    );

-- --- voucher_numberings ------------------------------------------------------

ALTER TABLE voucher_numberings ENABLE ROW LEVEL SECURITY;
ALTER TABLE voucher_numberings FORCE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS voucher_numberings_tenant_isolation_policy ON voucher_numberings;

CREATE POLICY voucher_numberings_tenant_isolation_policy ON voucher_numberings
    FOR ALL
    USING (
        tenant_id = nullif(current_setting('app.tenant_id', true), '')::uuid
    )
    WITH CHECK (
        tenant_id = nullif(current_setting('app.tenant_id', true), '')::uuid
    );

-- --- Platform cleanup role permissions --------------------------------------
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'erp_platform') THEN
        GRANT SELECT, UPDATE ON TABLE idempotency_keys TO erp_platform;
    END IF;
END
$$;
