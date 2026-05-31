-- School calendar: per-tenant holidays. Working days are stored in schools.settings JSONB
-- (no schema needed). New tables created after V37 must opt into RLS explicitly — the V37
-- DO-block only covered tables that existed then.

CREATE TABLE school_holidays (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id    UUID NOT NULL REFERENCES schools (id) ON DELETE CASCADE,
    holiday_date DATE NOT NULL,
    name         VARCHAR(120) NOT NULL,
    type         VARCHAR(20)  NOT NULL DEFAULT 'HOLIDAY',  -- HOLIDAY | EVENT | EXAM | VACATION
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_school_holiday UNIQUE (school_id, holiday_date)
);

CREATE INDEX idx_school_holidays_school_date ON school_holidays (school_id, holiday_date);

-- Tenant isolation, mirroring the V37 policy (NULLIF guards the empty-string-after-RESET case).
ALTER TABLE school_holidays ENABLE ROW LEVEL SECURITY;
ALTER TABLE school_holidays FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_isolation ON school_holidays;
CREATE POLICY tenant_isolation ON school_holidays
    USING (
        NULLIF(current_setting('app.current_tenant', true), '') IS NULL
        OR school_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid
    )
    WITH CHECK (
        NULLIF(current_setting('app.current_tenant', true), '') IS NULL
        OR school_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid
    );
