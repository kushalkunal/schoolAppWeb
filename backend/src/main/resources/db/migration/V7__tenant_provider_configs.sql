-- =====================================================================================
-- V7 — Per-tenant provider configurations
--
-- A row per (school_id, concern) telling the dispatcher beans WHICH external provider this
-- school uses and HOW to authenticate against it. Replaces the current JVM-global env-var
-- model — slice 1 wired the SaaS plumbing; this slice makes the actual provider switch
-- a per-tenant decision.
--
-- Concerns:
--   WHATSAPP   provider ∈ {WATI, TWILIO, INTERAKT, LOGGING}
--   EMAIL      provider ∈ {SMTP, SES, MAILGUN, SENDGRID, LOGGING}
--   PAYMENT    provider ∈ {STRIPE, RAZORPAY, LOGGING}
--   STORAGE    provider ∈ {LOCAL, S3} (where S3 endpoint can be R2/B2/MinIO/AWS)
--   OCR        provider ∈ {GOOGLE_CLOUD_VISION, LOGGING}
--   LLM        provider ∈ {OPENAI, ANTHROPIC, GEMINI, LOGGING}
--
-- config (JSONB) carries provider-specific knobs. Sensitive fields (api_token, secret_key,
-- webhook_secret, password) are AES-GCM encrypted at the application layer before write —
-- a Postgres dump on its own does NOT leak credentials.
-- =====================================================================================

CREATE TABLE tenant_provider_configs (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id           UUID NOT NULL REFERENCES schools(id) ON DELETE CASCADE,
    concern             VARCHAR(20) NOT NULL,
        -- WHATSAPP | EMAIL | PAYMENT | STORAGE | OCR | LLM
    provider            VARCHAR(40) NOT NULL,
        -- WATI | SMTP | STRIPE | RAZORPAY | S3 | GOOGLE_CLOUD_VISION | OPENAI | ANTHROPIC | GEMINI | LOGGING ...
    -- Encrypted JSON. Plaintext fields are read-only / non-sensitive (e.g. region, bucket
    -- name); secret fields are stored as base64(ciphertext || nonce) at the value level so
    -- this column itself is fully usable by ops queries.
    config              JSONB NOT NULL DEFAULT '{}'::jsonb,
    is_active           BOOLEAN NOT NULL DEFAULT TRUE,
    verified_at         TIMESTAMPTZ,             -- last successful credential round-trip
    last_error          TEXT,                    -- last verification failure (nullable)
    note                TEXT,
    created_by_id       UUID,                    -- platform admin or school owner who set
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    -- A school has at most one active provider per concern. Switching providers means
    -- updating the row, not adding a new one.
    UNIQUE(school_id, concern)
);

CREATE INDEX idx_tpc_school ON tenant_provider_configs(school_id);
CREATE INDEX idx_tpc_concern_provider ON tenant_provider_configs(concern, provider);
