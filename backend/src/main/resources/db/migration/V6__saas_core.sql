-- =====================================================================================
-- V6 — SaaS core
--
-- Adds:
--   plans                     catalog of subscription plans (FREE / STARTER / GROWTH / ENTERPRISE)
--   plan_features             which feature keys each plan includes
--   plan_limits               numeric ceilings per plan (messages/mo, storage bytes, students, ...)
--   features                  catalog of toggleable features (default enabled?)
--   feature_overrides         per-school enable/disable overriding the plan
--   subscriptions             one row per school, current subscription state
--   subscription_events       audit trail of state transitions on subscriptions
--   usage_counters            per-school metric counters keyed by period (YYYY-MM or ALL_TIME)
--
-- Design notes:
--   - "plans" and "features" are platform-level catalogs — no school_id, not multi-tenant.
--   - "feature_overrides", "subscriptions", "subscription_events", "usage_counters" ARE
--     tenant-scoped — they carry school_id and FK to schools(id).
--   - "subscription_events" intentionally has no FK to subscriptions(id) so we can record
--     a final CANCELLED row even after a subscription is hard-deleted in the future.
--   - All times TIMESTAMPTZ. Monetary fields (when added) will be BIGINT paise.
-- =====================================================================================


-- =====================================================================================
-- PLANS CATALOG
-- =====================================================================================
CREATE TABLE plans (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code            VARCHAR(40) NOT NULL UNIQUE,    -- FREE | STARTER | GROWTH | ENTERPRISE
    name            VARCHAR(100) NOT NULL,
    description     TEXT,
    monthly_price_paise  BIGINT NOT NULL DEFAULT 0,  -- 0 for FREE; reference only — billing TBD
    is_active       BOOLEAN NOT NULL DEFAULT TRUE,
    sort_order      INTEGER NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Each plan can include any number of features. Compound primary key prevents duplicates.
CREATE TABLE plan_features (
    plan_id         UUID NOT NULL REFERENCES plans(id) ON DELETE CASCADE,
    feature_key     VARCHAR(80) NOT NULL,
    PRIMARY KEY (plan_id, feature_key)
);
CREATE INDEX idx_plan_features_key ON plan_features(feature_key);

-- Numeric limits per plan, keyed by metric name. -1 == unlimited (sentinel).
CREATE TABLE plan_limits (
    plan_id         UUID NOT NULL REFERENCES plans(id) ON DELETE CASCADE,
    metric          VARCHAR(40) NOT NULL,
    limit_value     BIGINT NOT NULL,    -- -1 = unlimited
    PRIMARY KEY (plan_id, metric)
);


-- =====================================================================================
-- FEATURE CATALOG
-- =====================================================================================
CREATE TABLE features (
    feature_key     VARCHAR(80) PRIMARY KEY,    -- e.g. FEE, ACADEMICS, MIGRATION_OCR, ANALYTICS
    name            VARCHAR(120) NOT NULL,
    description     TEXT,
    default_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    -- Free-form grouping for the admin UI (e.g. "Core", "Premium", "Add-on")
    category        VARCHAR(40),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Per-school override. If a row exists, its `enabled` wins over the plan's inclusion.
-- Absence of a row = "fall back to plan_features". JSONB `config` lets a feature carry
-- per-school knobs without growing the schema (e.g. {"providerProfile": "WATI_PRO"}).
CREATE TABLE feature_overrides (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id       UUID NOT NULL REFERENCES schools(id) ON DELETE CASCADE,
    feature_key     VARCHAR(80) NOT NULL REFERENCES features(feature_key) ON DELETE CASCADE,
    enabled         BOOLEAN NOT NULL,
    config          JSONB NOT NULL DEFAULT '{}'::jsonb,
    note            TEXT,                       -- why this override was set (audit)
    updated_by_id   UUID,                       -- platform-admin or school-owner who toggled
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE(school_id, feature_key)
);
CREATE INDEX idx_feature_overrides_school ON feature_overrides(school_id);


-- =====================================================================================
-- SUBSCRIPTIONS
-- =====================================================================================
CREATE TABLE subscriptions (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id               UUID NOT NULL REFERENCES schools(id) ON DELETE CASCADE,
    plan_id                 UUID NOT NULL REFERENCES plans(id),
    status                  VARCHAR(20) NOT NULL,
        -- TRIAL | ACTIVE | PAST_DUE | SUSPENDED | CANCELLED
    trial_ends_at           TIMESTAMPTZ,
    current_period_start    TIMESTAMPTZ,
    current_period_end      TIMESTAMPTZ,
    -- External billing-provider correlation. Nullable until we wire Stripe Billing.
    external_customer_id    VARCHAR(120),
    external_subscription_id VARCHAR(120),
    suspension_reason       TEXT,
    cancelled_at            TIMESTAMPTZ,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    -- One active subscription per school. (If we ever support multiple concurrent
    -- subscriptions for add-ons, drop this and add a partial unique on (school_id, status='ACTIVE').)
    UNIQUE(school_id)
);
CREATE INDEX idx_subscriptions_status ON subscriptions(status);


-- =====================================================================================
-- SUBSCRIPTION EVENTS (audit trail)
-- =====================================================================================
CREATE TABLE subscription_events (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id       UUID NOT NULL,                          -- denormalised for fast lookups
    subscription_id UUID,                                   -- NOT enforced as FK on purpose
    event_type      VARCHAR(40) NOT NULL,
        -- TRIAL_STARTED | PLAN_CHANGED | SUSPENDED | RESUMED | CANCELLED | TRIAL_EXPIRED
    from_plan_code  VARCHAR(40),
    to_plan_code    VARCHAR(40),
    from_status     VARCHAR(20),
    to_status       VARCHAR(20),
    actor_staff_id  UUID,
    note            TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_sub_events_school ON subscription_events(school_id, created_at DESC);


-- =====================================================================================
-- USAGE COUNTERS
-- =====================================================================================
-- Per (school, metric, period_key). period_key:
--   YYYY-MM   monthly counters (MESSAGES_SENT_MONTHLY, OCR_PAGES_MONTHLY)
--   'ALL'     lifetime counters (STORAGE_BYTES, STUDENTS_COUNT, STAFF_COUNT)
CREATE TABLE usage_counters (
    school_id       UUID NOT NULL REFERENCES schools(id) ON DELETE CASCADE,
    metric          VARCHAR(40) NOT NULL,
    period_key      VARCHAR(10) NOT NULL,    -- 'YYYY-MM' or 'ALL'
    count_value     BIGINT NOT NULL DEFAULT 0,
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (school_id, metric, period_key)
);
CREATE INDEX idx_usage_school_metric ON usage_counters(school_id, metric);


-- =====================================================================================
-- SEED DATA — Plans + features catalogs
-- =====================================================================================

-- Feature catalog. Keep keys short, stable, screaming-snake-case. Never rename.
INSERT INTO features (feature_key, name, description, default_enabled, category) VALUES
    -- Core: bundled with FREE so every school can sign up and be useful.
    ('STUDENTS',            'Student management',           'Roster, parents, sibling detection, profile + timeline', TRUE,  'Core'),
    ('ATTENDANCE',          'Attendance',                   'Reverse-marking attendance + sibling-aware alerts',      TRUE,  'Core'),
    ('SCHOOL_PROFILE',      'School profile & onboarding',  'School settings, classes, staff, onboarding wizard',     TRUE,  'Core'),

    -- Standard: included from STARTER upward.
    ('FEE',                 'Fee management',               'Quick-collect, invoices, receipts, dashboard, reminders', TRUE,  'Standard'),
    ('ACADEMICS',           'Academics',                    'Subjects, exams, marks grid, report cards',              TRUE,  'Standard'),
    ('COMMUNICATION_CORE',  'WhatsApp + email dispatchers', 'Outbound alerts, OTPs, receipts via WhatsApp / SMTP',    TRUE,  'Standard'),
    ('CIRCULARS',           'Circulars',                    'Bulk broadcast WhatsApp circulars to parents',           FALSE, 'Standard'),
    ('WHATSAPP_INBOX',      'WhatsApp inbox',               'Inbound parent replies routed to class teachers',        FALSE, 'Standard'),

    -- Premium: GROWTH+ only.
    ('ANALYTICS',           'Analytics + dashboard',        'Principal dashboard, alerts, at-risk scoring',           FALSE, 'Premium'),
    ('SUBSTITUTE_TEACHERS', 'Substitute teacher scheduling','One-day substitute assignments + notifications',         FALSE, 'Premium'),
    ('TEACHER_ASSIGNMENTS', 'Teacher-subject assignments',  'Who teaches what — drives marks-entry scoping',          FALSE, 'Premium'),
    ('EXAM_ELIGIBILITY',    'Exam eligibility (attendance%)', 'Per-student attendance-% gating for exam hall',        FALSE, 'Premium'),
    ('AUDIT_LOG',           'Audit log',                    'Compliance-grade change log for owners + principals',    FALSE, 'Premium'),

    -- Add-ons: ENTERPRISE or opt-in only.
    ('PAPER_MIGRATION',     'OCR paper migration',          'Photograph paper registers/receipts → AI extraction',    FALSE, 'Add-on'),
    ('DATA_EXPORT',         'Data export (XLSX/CSV)',       'Streaming exports of students, fees, attendance',        FALSE, 'Add-on'),
    ('MOBILE_SYNC',         'Mobile offline sync',          'React Native teacher app sync API',                      FALSE, 'Add-on'),
    ('DPDP_REQUESTS',       'Data-deletion requests',       'DPDP Act 2023 deletion-request intake',                  FALSE, 'Add-on'),
    ('PAYMENT_GATEWAY',     'Online payments',              'Stripe / Razorpay payment links + auto-receipts',        FALSE, 'Add-on'),
    ('STUDENT_DOCUMENTS',   'Student documents',            'Upload + manage admission forms, birth certs, TCs',      FALSE, 'Add-on');

-- Plans catalog. Prices are reference values — billing wiring comes in a later slice.
INSERT INTO plans (code, name, description, monthly_price_paise, sort_order) VALUES
    ('FREE',       'Free',
        'Trial / pilot — core features for up to 50 students. No paid integrations.',
        0, 10),
    ('STARTER',    'Starter',
        'Single small school. Fees + academics included. WhatsApp via paid BSP.',
        99900, 20),       -- ₹999/mo
    ('GROWTH',     'Growth',
        'Adds analytics, dashboards, premium modules. Up to 500 students.',
        249900, 30),      -- ₹2,499/mo
    ('ENTERPRISE', 'Enterprise',
        'Everything, no soft limits, priority support, custom branding.',
        599900, 40);      -- ₹5,999/mo

-- Plan → features mapping. Lower plans are subsets of higher; we keep this explicit
-- (not inherited) so changing a higher-tier plan never accidentally changes a lower one.
WITH p AS (SELECT id, code FROM plans)
INSERT INTO plan_features (plan_id, feature_key)
SELECT p.id, f.feature_key
FROM p
CROSS JOIN LATERAL (VALUES
    -- FREE: just core
    ('FREE',       'STUDENTS'),
    ('FREE',       'ATTENDANCE'),
    ('FREE',       'SCHOOL_PROFILE'),

    -- STARTER: core + standard
    ('STARTER',    'STUDENTS'),
    ('STARTER',    'ATTENDANCE'),
    ('STARTER',    'SCHOOL_PROFILE'),
    ('STARTER',    'FEE'),
    ('STARTER',    'ACADEMICS'),
    ('STARTER',    'COMMUNICATION_CORE'),
    ('STARTER',    'CIRCULARS'),

    -- GROWTH: + premium
    ('GROWTH',     'STUDENTS'),
    ('GROWTH',     'ATTENDANCE'),
    ('GROWTH',     'SCHOOL_PROFILE'),
    ('GROWTH',     'FEE'),
    ('GROWTH',     'ACADEMICS'),
    ('GROWTH',     'COMMUNICATION_CORE'),
    ('GROWTH',     'CIRCULARS'),
    ('GROWTH',     'WHATSAPP_INBOX'),
    ('GROWTH',     'ANALYTICS'),
    ('GROWTH',     'SUBSTITUTE_TEACHERS'),
    ('GROWTH',     'TEACHER_ASSIGNMENTS'),
    ('GROWTH',     'EXAM_ELIGIBILITY'),
    ('GROWTH',     'AUDIT_LOG'),
    ('GROWTH',     'DATA_EXPORT'),
    ('GROWTH',     'STUDENT_DOCUMENTS'),

    -- ENTERPRISE: everything
    ('ENTERPRISE', 'STUDENTS'),
    ('ENTERPRISE', 'ATTENDANCE'),
    ('ENTERPRISE', 'SCHOOL_PROFILE'),
    ('ENTERPRISE', 'FEE'),
    ('ENTERPRISE', 'ACADEMICS'),
    ('ENTERPRISE', 'COMMUNICATION_CORE'),
    ('ENTERPRISE', 'CIRCULARS'),
    ('ENTERPRISE', 'WHATSAPP_INBOX'),
    ('ENTERPRISE', 'ANALYTICS'),
    ('ENTERPRISE', 'SUBSTITUTE_TEACHERS'),
    ('ENTERPRISE', 'TEACHER_ASSIGNMENTS'),
    ('ENTERPRISE', 'EXAM_ELIGIBILITY'),
    ('ENTERPRISE', 'AUDIT_LOG'),
    ('ENTERPRISE', 'PAPER_MIGRATION'),
    ('ENTERPRISE', 'DATA_EXPORT'),
    ('ENTERPRISE', 'MOBILE_SYNC'),
    ('ENTERPRISE', 'DPDP_REQUESTS'),
    ('ENTERPRISE', 'PAYMENT_GATEWAY'),
    ('ENTERPRISE', 'STUDENT_DOCUMENTS')
) AS f(plan_code, feature_key)
WHERE p.code = f.plan_code;

-- Plan limits. -1 == unlimited.
WITH p AS (SELECT id, code FROM plans)
INSERT INTO plan_limits (plan_id, metric, limit_value)
SELECT p.id, l.metric, l.limit_value
FROM p
CROSS JOIN LATERAL (VALUES
    ('FREE',       'STUDENTS_COUNT',         50::bigint),
    ('FREE',       'STAFF_COUNT',            10::bigint),
    ('FREE',       'MESSAGES_SENT_MONTHLY',  500::bigint),
    ('FREE',       'STORAGE_BYTES',          (100 * 1024 * 1024)::bigint),    -- 100 MB
    ('FREE',       'OCR_PAGES_MONTHLY',      0::bigint),                       -- gated

    ('STARTER',    'STUDENTS_COUNT',         300::bigint),
    ('STARTER',    'STAFF_COUNT',            30::bigint),
    ('STARTER',    'MESSAGES_SENT_MONTHLY',  5000::bigint),
    ('STARTER',    'STORAGE_BYTES',          (2::bigint * 1024 * 1024 * 1024)),    -- 2 GB
    ('STARTER',    'OCR_PAGES_MONTHLY',      100::bigint),

    ('GROWTH',     'STUDENTS_COUNT',         800::bigint),
    ('GROWTH',     'STAFF_COUNT',            80::bigint),
    ('GROWTH',     'MESSAGES_SENT_MONTHLY',  20000::bigint),
    ('GROWTH',     'STORAGE_BYTES',          (10::bigint * 1024 * 1024 * 1024)),   -- 10 GB
    ('GROWTH',     'OCR_PAGES_MONTHLY',      500::bigint),

    ('ENTERPRISE', 'STUDENTS_COUNT',         -1::bigint),
    ('ENTERPRISE', 'STAFF_COUNT',            -1::bigint),
    ('ENTERPRISE', 'MESSAGES_SENT_MONTHLY',  -1::bigint),
    ('ENTERPRISE', 'STORAGE_BYTES',          -1::bigint),
    ('ENTERPRISE', 'OCR_PAGES_MONTHLY',      -1::bigint)
) AS l(plan_code, metric, limit_value)
WHERE p.code = l.plan_code;

-- Backfill: every existing school (from prior migrations or active dev runs) gets a TRIAL
-- subscription on FREE so the suspension guard doesn't lock them out on first deploy of V6.
-- New schools created after V6 will get their subscription via SchoolService.createSchool.
INSERT INTO subscriptions (school_id, plan_id, status, trial_ends_at, current_period_start, current_period_end)
SELECT s.id,
       (SELECT id FROM plans WHERE code = 'FREE'),
       'TRIAL',
       NOW() + INTERVAL '14 days',
       NOW(),
       NOW() + INTERVAL '14 days'
FROM schools s
WHERE NOT EXISTS (SELECT 1 FROM subscriptions sub WHERE sub.school_id = s.id);

-- Audit row for each backfilled subscription.
INSERT INTO subscription_events (school_id, subscription_id, event_type, to_plan_code, to_status, note)
SELECT sub.school_id, sub.id, 'TRIAL_STARTED', 'FREE', 'TRIAL',
       'Backfilled by V6 migration'
FROM subscriptions sub;
