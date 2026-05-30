-- V38 — Maker-checker approval engine (audit fix #6/#7).
-- Stages money-moving requests (fee discounts, refunds) so they take effect only when a
-- different user approves them, with a full audit trail.

CREATE TABLE approval_requests (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id         UUID NOT NULL REFERENCES schools(id),
    type              VARCHAR(40) NOT NULL,
    status            VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    subject_id        UUID,
    payload_json      TEXT,
    amount_paise      BIGINT NOT NULL DEFAULT 0,
    summary           TEXT,
    requested_by_id   UUID NOT NULL,
    requested_by_role VARCHAR(30),
    decided_by_id     UUID,
    decided_at        TIMESTAMPTZ,
    decision_note     TEXT,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ,
    created_by_id     UUID
);

-- Approvals inbox query: pending items for a tenant, newest first.
CREATE INDEX idx_approval_requests_school_status
    ON approval_requests(school_id, status, created_at DESC);

-- Row-level security: V37 ran before this table existed, so apply the same tenant-isolation
-- policy here. (school_app is granted automatically via V37's ALTER DEFAULT PRIVILEGES.)
ALTER TABLE approval_requests ENABLE ROW LEVEL SECURITY;
ALTER TABLE approval_requests FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON approval_requests
    USING (
        NULLIF(current_setting('app.current_tenant', true), '') IS NULL
        OR school_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid
    )
    WITH CHECK (
        NULLIF(current_setting('app.current_tenant', true), '') IS NULL
        OR school_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid
    );
