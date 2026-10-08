-- ==============================================================================
-- ERP INTEGRATION HUB - V1 CREATE TABLES
-- ==============================================================================

-- 1. Configuraciones de Proveedores Externos
CREATE TABLE IF NOT EXISTS provider_configs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    provider VARCHAR(50) NOT NULL UNIQUE,
    base_url VARCHAR(255) NOT NULL,
    credentials_ref VARCHAR(255) NOT NULL,
    timeout_ms INTEGER DEFAULT 5000,
    enabled BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- 2. Claves de Idempotencia por Inquilino (Evita cobros y facturas duplicadas)
CREATE TABLE IF NOT EXISTS idempotency_keys (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    scope VARCHAR(50) NOT NULL,
    key_val VARCHAR(255) NOT NULL,
    response_hash VARCHAR(255),
    response_payload TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    CONSTRAINT uk_idempotency_tenant_scope_key UNIQUE (tenant_id, scope, key_val)
);

CREATE INDEX IF NOT EXISTS idx_idempotency_tenant ON idempotency_keys(tenant_id);

-- 3. Bitácora de Intentos de Integración (Auditoría y trazabilidad)
CREATE TABLE IF NOT EXISTS integration_attempts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    provider VARCHAR(50) NOT NULL,
    operation VARCHAR(100) NOT NULL,
    reference_id UUID NOT NULL,
    attempt_number INTEGER NOT NULL,
    status VARCHAR(20) NOT NULL,
    error_detail TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_attempts_tenant_ref ON integration_attempts(tenant_id, reference_id);
CREATE INDEX IF NOT EXISTS idx_attempts_status ON integration_attempts(status);

-- 4. Cola de Reintentos Asíncronos (Exponential Backoff y DLQ)
CREATE TABLE IF NOT EXISTS integration_retry_tasks (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    provider VARCHAR(50) NOT NULL,
    operation VARCHAR(100) NOT NULL,
    reference_id UUID NOT NULL,
    payload TEXT NOT NULL,
    attempts_count INTEGER DEFAULT 0,
    max_attempts INTEGER DEFAULT 5,
    next_retry_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    status VARCHAR(20) DEFAULT 'PENDING',
    last_error TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_retry_tasks_status_next ON integration_retry_tasks(status, next_retry_at);
