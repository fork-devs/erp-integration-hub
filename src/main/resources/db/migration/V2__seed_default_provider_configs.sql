-- ==============================================================================
-- ERP INTEGRATION HUB - V2 SEED PROVIDER CONFIGS (SANDBOX DEFAULTS)
-- ==============================================================================

INSERT INTO provider_configs (id, provider, base_url, credentials_ref, timeout_ms, enabled, created_at)
VALUES 
    (gen_random_uuid(), 'NUBEFACT', 'https://api.nubefact.com/api/v1', 'vault:secret/nubefact/token', 8000, true, NOW()),
    (gen_random_uuid(), 'STRIPE', 'https://api.stripe.com/v1', 'vault:secret/stripe/api_key', 8000, true, NOW()),
    (gen_random_uuid(), 'RENIEC_SUNAT', 'https://apiperu.dev/api/ruc', 'vault:secret/apiperu/token', 5000, true, NOW()),
    (gen_random_uuid(), 'SUNARP', 'https://apiperu.dev/api/placa', 'vault:secret/apiperu/token', 5000, true, NOW())
ON CONFLICT (provider) DO NOTHING;
