-- ============================================================================
-- V24 — Row-Level Security (A1, Layer 1): the database-enforced tenant boundary.
-- ============================================================================
-- Until now tenant isolation was application convention only: every query had to
-- remember `AND school_id = ?`. The audit found cross-tenant IDOR where it was
-- forgotten. This migration makes cross-tenant reads impossible *at the database*,
-- regardless of what the application code does.
--
-- HOW IT WORKS
--   * A non-superuser role `school_app`. The runtime SET ROLEs into it on every
--     connection (see RlsTenantConnectionProvider). RLS does not apply to
--     superusers, so the app MUST act as this role for policies to take effect.
--   * Each tenant-scoped table (any table with a `school_id` column) gets a policy
--     keyed on the `app.current_tenant` GUC, which the connection provider sets
--     from the authenticated request's tenant.
--   * GUC unset  -> the OR-branch makes the policy permissive. This is the
--     "SYSTEM" path used by trusted, tenant-less work: login/OTP/signup,
--     webhooks (tenant resolved from payload), platform admin, and the many
--     cross-tenant schedulers (OutboxPoller, TrialExpiryScheduler, detectors...).
--     These keep working exactly as before. Authenticated user requests always
--     have the GUC set and are therefore restricted to their own tenant.
--
-- This is intentionally permissive-when-unset rather than deny-by-default: it is a
-- strict improvement over today (no DB enforcement at all) and is safe to roll out
-- without first auditing every tenant-less code path. A later migration can tighten
-- to deny-by-default once each SYSTEM path is explicitly marked.
--
-- PROD REQUIREMENT: the Flyway/migration user must be able to CREATE ROLE
-- (superuser or a role with CREATEROLE).
-- ============================================================================

DO $rls$
DECLARE
    r record;
BEGIN
    -- 1. The runtime role the application SET ROLEs into. NOLOGIN: it is only ever
    --    entered via SET ROLE from the real connecting user, never logged into directly.
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'school_app') THEN
        CREATE ROLE school_app NOLOGIN;
    END IF;

    -- The connecting user must be a member of school_app to SET ROLE into it
    -- (superusers may SET ROLE to anyone, so this is a no-op for them but required
    -- when the app connects as a plain non-superuser in production).
    EXECUTE format('GRANT school_app TO %I', current_user);

    -- 2. Privileges. school_app needs to actually use the schema + every table.
    EXECUTE 'GRANT USAGE ON SCHEMA public TO school_app';
    EXECUTE 'GRANT ALL ON ALL TABLES IN SCHEMA public TO school_app';
    EXECUTE 'GRANT ALL ON ALL SEQUENCES IN SCHEMA public TO school_app';
    -- Tables created by future migrations (run as the same user) auto-grant to school_app.
    ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON TABLES TO school_app;
    ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON SEQUENCES TO school_app;

    -- 3. Enable RLS + the tenant-isolation policy on every table carrying a school_id.
    --    Iterating information_schema means new tenant tables are covered the moment a
    --    future migration re-runs this block (kept idempotent via DROP POLICY IF EXISTS).
    FOR r IN
        SELECT c.table_name
        FROM information_schema.columns c
        JOIN information_schema.tables t
          ON t.table_schema = c.table_schema AND t.table_name = c.table_name
        WHERE c.table_schema = 'public'
          AND c.column_name = 'school_id'
          AND t.table_type = 'BASE TABLE'
    LOOP
        EXECUTE format('ALTER TABLE %I ENABLE ROW LEVEL SECURITY', r.table_name);
        -- FORCE so the policy also binds the table owner if the app ever connects as
        -- the owner (non-superuser) rather than via SET ROLE.
        EXECUTE format('ALTER TABLE %I FORCE ROW LEVEL SECURITY', r.table_name);
        EXECUTE format('DROP POLICY IF EXISTS tenant_isolation ON %I', r.table_name);
        -- NULLIF(...,'') is load-bearing: RESETting a runtime-only GUC leaves it as the empty
        -- string (not NULL), and ''::uuid throws. Coercing '' -> NULL makes both "unset" and
        -- "reset" resolve to the permissive SYSTEM branch, and the ::uuid cast only ever sees a
        -- real UUID (or NULL, which casts cleanly).
        EXECUTE format($pol$
            CREATE POLICY tenant_isolation ON %I
            USING (
                NULLIF(current_setting('app.current_tenant', true), '') IS NULL
                OR school_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid
            )
            WITH CHECK (
                NULLIF(current_setting('app.current_tenant', true), '') IS NULL
                OR school_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid
            )
        $pol$, r.table_name);
    END LOOP;
END
$rls$;
