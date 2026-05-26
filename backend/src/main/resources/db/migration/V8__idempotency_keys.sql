-- =====================================================================================
-- V8 — Idempotency-Key support
--
-- A row per (tenant, method, path, idempotency_key). A mutating POST repeated with the
-- same key + same body returns the cached response instead of executing twice. Clients
-- use this to safely retry on network failure (mobile, BSP webhook retries, etc.).
-- =====================================================================================

CREATE TABLE idempotency_keys (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    -- tenant_id nullable for pre-auth idempotent calls (e.g. signup) — those still get
    -- de-duped, just keyed on the client identifier instead of the tenant.
    tenant_id       UUID,
    idempotency_key VARCHAR(120) NOT NULL,
    method          VARCHAR(10)  NOT NULL,
    path            VARCHAR(500) NOT NULL,
    body_hash       VARCHAR(64)  NOT NULL,    -- hex SHA-256 of request body
    status_code     INTEGER      NOT NULL,
    response_body   TEXT,                      -- captured response payload (may be large; cap in app)
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    expires_at      TIMESTAMPTZ  NOT NULL,     -- typically NOW() + 24 hours
    UNIQUE(tenant_id, method, path, idempotency_key)
);
CREATE INDEX idx_idem_expires ON idempotency_keys(expires_at);
