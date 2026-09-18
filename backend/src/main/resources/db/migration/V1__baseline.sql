-- =====================================================================
-- V1__baseline.sql — consolidated baseline schema.
-- Squashed from the original 45 migrations (V1..V45) on 2026-06-03.
-- Regenerate by concatenating the historical migrations in version order.
-- Intended for FRESH databases only (single Flyway baseline migration).
-- =====================================================================


-- ─── from: V1__initial_schema.sql ───
-- ============================================================
-- V1: Initial schema for School Management System
-- Creates all core tables per PHASE2_BACKEND_SPRINGBOOT_LLD.md
-- Multi-tenant: every domain table has school_id
-- ============================================================

CREATE EXTENSION IF NOT EXISTS "pgcrypto";   -- gen_random_uuid()
CREATE EXTENSION IF NOT EXISTS "pg_trgm";    -- trigram fuzzy search

-- ============================================================
-- SCHOOL & CONFIGURATION
-- ============================================================
CREATE TABLE schools (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name            VARCHAR(255) NOT NULL,
    principal_name  VARCHAR(255) NOT NULL,
    phone           VARCHAR(15) NOT NULL UNIQUE,
    email           VARCHAR(255),
    address         TEXT,
    city            VARCHAR(100),
    state           VARCHAR(100) NOT NULL,
    pincode         VARCHAR(10),
    board           VARCHAR(20) NOT NULL,
    logo_url        TEXT,
    whatsapp_number VARCHAR(15),
    wa_configured   BOOLEAN NOT NULL DEFAULT FALSE,
    is_active       BOOLEAN NOT NULL DEFAULT TRUE,
    settings        JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE academic_years (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id       UUID NOT NULL REFERENCES schools(id),
    name            VARCHAR(20) NOT NULL,
    start_date      DATE NOT NULL,
    end_date        DATE NOT NULL,
    is_current      BOOLEAN NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE(school_id, name)
);
-- Partial unique index: only one current academic year per school
CREATE UNIQUE INDEX uq_academic_years_current
    ON academic_years(school_id) WHERE is_current = TRUE;

CREATE TABLE school_classes (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id       UUID NOT NULL REFERENCES schools(id),
    name            VARCHAR(50) NOT NULL,
    sort_order      INTEGER NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE(school_id, name)
);

CREATE TABLE sections (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id           UUID NOT NULL REFERENCES schools(id),
    class_id            UUID NOT NULL REFERENCES school_classes(id),
    academic_year_id    UUID NOT NULL REFERENCES academic_years(id),
    name                VARCHAR(10) NOT NULL,
    class_teacher_id    UUID,  -- FK added later once staff exists
    max_strength        INTEGER DEFAULT 50,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE(school_id, class_id, academic_year_id, name)
);

-- ============================================================
-- PEOPLE: STAFF, PARENTS, STUDENTS
-- ============================================================
CREATE TABLE staff (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id       UUID NOT NULL REFERENCES schools(id),
    first_name      VARCHAR(100) NOT NULL,
    last_name       VARCHAR(100),
    phone           VARCHAR(15) NOT NULL,
    email           VARCHAR(255),
    role            VARCHAR(30) NOT NULL,
    gender          VARCHAR(10),
    date_of_joining DATE,
    is_active       BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE(school_id, phone)
);

ALTER TABLE sections
    ADD CONSTRAINT fk_sections_class_teacher
    FOREIGN KEY (class_teacher_id) REFERENCES staff(id);

CREATE TABLE parents (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id       UUID NOT NULL REFERENCES schools(id),
    name            VARCHAR(200) NOT NULL,
    phone           VARCHAR(15) NOT NULL,
    email           VARCHAR(255),
    relation_type   VARCHAR(20),
    occupation      VARCHAR(100),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE(school_id, phone)
);

CREATE TABLE students (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id           UUID NOT NULL REFERENCES schools(id),
    first_name          VARCHAR(100) NOT NULL,
    last_name           VARCHAR(100),
    admission_number    VARCHAR(50),
    gender              VARCHAR(10),
    date_of_birth       DATE,
    blood_group         VARCHAR(5),
    address             TEXT,
    photo_url           TEXT,
    is_active           BOOLEAN NOT NULL DEFAULT TRUE,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
-- Partial unique: admission numbers unique within school, but null allowed
CREATE UNIQUE INDEX uq_students_admission_number
    ON students(school_id, admission_number)
    WHERE admission_number IS NOT NULL;

-- Trigram index for fuzzy name search (used by OCR matching + student search)
CREATE INDEX idx_students_name_trgm ON students
    USING GIN ((first_name || ' ' || COALESCE(last_name, '')) gin_trgm_ops);

CREATE INDEX idx_students_school_active ON students(school_id, is_active)
    WHERE is_active = TRUE;

CREATE TABLE student_parent_links (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    student_id      UUID NOT NULL REFERENCES students(id) ON DELETE CASCADE,
    parent_id       UUID NOT NULL REFERENCES parents(id) ON DELETE CASCADE,
    relation        VARCHAR(20) NOT NULL,
    is_primary      BOOLEAN NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE(student_id, parent_id, relation)
);
CREATE INDEX idx_student_parent_primary
    ON student_parent_links(student_id, is_primary)
    WHERE is_primary = TRUE;

CREATE TABLE student_enrollments (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    student_id          UUID NOT NULL REFERENCES students(id),
    school_id           UUID NOT NULL REFERENCES schools(id),
    academic_year_id    UUID NOT NULL REFERENCES academic_years(id),
    section_id          UUID NOT NULL REFERENCES sections(id),
    roll_number         INTEGER,
    status              VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    leaving_date        DATE,
    leaving_reason      TEXT,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE(student_id, academic_year_id)
);
CREATE INDEX idx_enrollments_section ON student_enrollments(section_id, status);

-- ============================================================
-- SUBJECTS & TEACHER ASSIGNMENTS
-- ============================================================
CREATE TABLE subjects (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id   UUID NOT NULL REFERENCES schools(id),
    name        VARCHAR(100) NOT NULL,
    code        VARCHAR(20),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE(school_id, name)
);

CREATE TABLE teacher_subject_assignments (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id           UUID NOT NULL REFERENCES schools(id),
    staff_id            UUID NOT NULL REFERENCES staff(id),
    subject_id          UUID NOT NULL REFERENCES subjects(id),
    section_id          UUID NOT NULL REFERENCES sections(id),
    academic_year_id    UUID NOT NULL REFERENCES academic_years(id),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE(staff_id, subject_id, section_id, academic_year_id)
);
CREATE INDEX idx_tsa_staff_year ON teacher_subject_assignments(staff_id, academic_year_id);

-- ============================================================
-- ATTENDANCE
-- ============================================================
CREATE TABLE attendance_records (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id           UUID NOT NULL REFERENCES schools(id),
    student_id          UUID NOT NULL REFERENCES students(id),
    section_id          UUID NOT NULL REFERENCES sections(id),
    date                DATE NOT NULL,
    status              VARCHAR(15) NOT NULL DEFAULT 'PRESENT',
    arrival_time        TIMESTAMPTZ,  -- populated when status = 'LATE'
    marked_by_id        UUID REFERENCES staff(id),
    note                TEXT,
    is_historical       BOOLEAN NOT NULL DEFAULT FALSE,
    synced_from_mobile  BOOLEAN NOT NULL DEFAULT FALSE,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE(student_id, date)
);
CREATE INDEX idx_attendance_school_date    ON attendance_records(school_id, date);
CREATE INDEX idx_attendance_student        ON attendance_records(student_id, date DESC);
CREATE INDEX idx_attendance_section_date   ON attendance_records(section_id, date);

-- ============================================================
-- FEE MANAGEMENT
-- ============================================================
CREATE TABLE fee_categories (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id   UUID NOT NULL REFERENCES schools(id),
    name        VARCHAR(100) NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE(school_id, name)
);

CREATE TABLE fee_heads (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id   UUID NOT NULL REFERENCES schools(id),
    name        VARCHAR(100) NOT NULL,
    is_active   BOOLEAN NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE(school_id, name)
);

CREATE TABLE fee_invoices (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id           UUID NOT NULL REFERENCES schools(id),
    student_id          UUID NOT NULL REFERENCES students(id),
    fee_head_id         UUID REFERENCES fee_heads(id),
    amount_due_paise    BIGINT NOT NULL,
    amount_paid_paise   BIGINT NOT NULL DEFAULT 0,
    due_date            DATE,
    status              VARCHAR(15) NOT NULL DEFAULT 'PENDING',
    is_opening_balance  BOOLEAN NOT NULL DEFAULT FALSE,
    academic_year_id    UUID REFERENCES academic_years(id),
    description         TEXT,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_fee_invoices_student ON fee_invoices(student_id, status);
CREATE INDEX idx_fee_invoices_school_status ON fee_invoices(school_id, status, due_date);
CREATE INDEX idx_fee_invoices_outstanding
    ON fee_invoices(school_id, status, due_date)
    WHERE status IN ('PENDING', 'PARTIAL');

CREATE TABLE fee_payments (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id           UUID NOT NULL REFERENCES schools(id),
    student_id          UUID NOT NULL REFERENCES students(id),
    invoice_id          UUID REFERENCES fee_invoices(id),
    fee_head_id         UUID REFERENCES fee_heads(id),
    amount_paise        BIGINT NOT NULL,
    payment_mode        VARCHAR(10) NOT NULL,
    receipt_number      VARCHAR(50) NOT NULL,
    receipt_pdf_url     TEXT,
    payment_date        DATE NOT NULL,
    collected_by_id     UUID REFERENCES staff(id),
    razorpay_order_id   VARCHAR(100),
    razorpay_payment_id VARCHAR(100),
    notes               TEXT,
    is_historical       BOOLEAN NOT NULL DEFAULT FALSE,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE(school_id, receipt_number)
);
CREATE INDEX idx_fee_payments_student ON fee_payments(student_id, payment_date DESC);
CREATE INDEX idx_fee_payments_school_date ON fee_payments(school_id, payment_date DESC);

-- ============================================================
-- ACADEMICS
-- ============================================================
CREATE TABLE exams (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id           UUID NOT NULL REFERENCES schools(id),
    academic_year_id    UUID NOT NULL REFERENCES academic_years(id),
    name                VARCHAR(100) NOT NULL,
    exam_type           VARCHAR(20),
    start_date          DATE,
    end_date            DATE,
    is_published        BOOLEAN NOT NULL DEFAULT FALSE,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_exams_year ON exams(academic_year_id);

CREATE TABLE exam_marks (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id       UUID NOT NULL REFERENCES schools(id),
    exam_id         UUID NOT NULL REFERENCES exams(id),
    student_id      UUID NOT NULL REFERENCES students(id),
    subject_id      UUID NOT NULL REFERENCES subjects(id),
    section_id      UUID NOT NULL REFERENCES sections(id),
    max_marks       NUMERIC(6,2) NOT NULL,
    obtained_marks  NUMERIC(6,2),
    is_absent       BOOLEAN NOT NULL DEFAULT FALSE,
    grade           VARCHAR(5),
    entered_by_id   UUID REFERENCES staff(id),
    is_draft        BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE(exam_id, student_id, subject_id)
);
CREATE INDEX idx_exam_marks_student ON exam_marks(student_id, exam_id);
CREATE INDEX idx_exam_marks_section ON exam_marks(section_id, exam_id);

CREATE TABLE report_cards (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id           UUID NOT NULL REFERENCES schools(id),
    student_id          UUID NOT NULL REFERENCES students(id),
    exam_id             UUID NOT NULL REFERENCES exams(id),
    total_marks         NUMERIC(7,2),
    obtained_marks      NUMERIC(7,2),
    percentage          NUMERIC(5,2),
    grade               VARCHAR(5),
    rank_in_class       INTEGER,
    teacher_remarks     TEXT,
    pdf_url             TEXT,
    wa_sent_at          TIMESTAMPTZ,
    wa_delivered_at     TIMESTAMPTZ,
    wa_read_at          TIMESTAMPTZ,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE(student_id, exam_id)
);

-- ============================================================
-- COMMUNICATION
-- ============================================================
CREATE TABLE circulars (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id           UUID NOT NULL REFERENCES schools(id),
    title               VARCHAR(255) NOT NULL,
    body                TEXT NOT NULL,
    target_type         VARCHAR(20) NOT NULL,
    target_ids          UUID[],
    language            VARCHAR(20) NOT NULL DEFAULT 'en',
    attachment_url      TEXT,
    sent_count          INTEGER NOT NULL DEFAULT 0,
    delivered_count     INTEGER NOT NULL DEFAULT 0,
    read_count          INTEGER NOT NULL DEFAULT 0,
    failed_count        INTEGER NOT NULL DEFAULT 0,
    created_by_id       UUID REFERENCES staff(id),
    scheduled_at        TIMESTAMPTZ,
    sent_at             TIMESTAMPTZ,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE notification_log (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id           UUID NOT NULL REFERENCES schools(id),
    event_type          VARCHAR(40) NOT NULL,
    recipient_phone     VARCHAR(15) NOT NULL,
    recipient_name      VARCHAR(200),
    student_id          UUID REFERENCES students(id),
    parent_id           UUID REFERENCES parents(id),
    circular_id         UUID REFERENCES circulars(id),
    channel             VARCHAR(10) NOT NULL DEFAULT 'WHATSAPP',
    message_body        TEXT,
    wa_message_id       VARCHAR(100),
    status              VARCHAR(15) NOT NULL DEFAULT 'QUEUED',
    sent_at             TIMESTAMPTZ,
    delivered_at        TIMESTAMPTZ,
    read_at             TIMESTAMPTZ,
    error_message       TEXT,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_notif_school_date ON notification_log(school_id, created_at DESC);
CREATE INDEX idx_notif_wa_id
    ON notification_log(wa_message_id) WHERE wa_message_id IS NOT NULL;

-- ============================================================
-- OCR MIGRATION JOBS
-- ============================================================
CREATE TABLE migration_jobs (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id       UUID NOT NULL REFERENCES schools(id),
    job_type        VARCHAR(20) NOT NULL,
    status          VARCHAR(20) NOT NULL DEFAULT 'UPLOADED',
    image_url       TEXT NOT NULL,
    raw_ocr_text    TEXT,
    extracted_json  JSONB,
    reviewed_json   JSONB,
    record_count    INTEGER,
    matched_count   INTEGER,
    error_message   TEXT,
    created_by_id   UUID REFERENCES staff(id),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    completed_at    TIMESTAMPTZ
);
CREATE INDEX idx_migration_jobs_school ON migration_jobs(school_id, status, created_at DESC);

-- ============================================================
-- AUDIT LOG
-- ============================================================
CREATE TABLE audit_log (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id       UUID,
    entity_type     VARCHAR(50) NOT NULL,
    entity_id       UUID NOT NULL,
    action          VARCHAR(10) NOT NULL,
    old_values      JSONB,
    new_values      JSONB,
    changed_by_id   UUID,
    changed_by_role VARCHAR(30),
    ip_address      VARCHAR(45),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_audit_entity ON audit_log(entity_type, entity_id, created_at DESC);
CREATE INDEX idx_audit_school ON audit_log(school_id, created_at DESC);

-- ============================================================
-- ALERTS (gap analysis §5.4)
-- ============================================================
CREATE TABLE alerts (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id       UUID NOT NULL REFERENCES schools(id),
    alert_type      VARCHAR(40) NOT NULL,
    severity        VARCHAR(10) NOT NULL DEFAULT 'MEDIUM',
    student_id      UUID REFERENCES students(id),
    section_id      UUID REFERENCES sections(id),
    title           VARCHAR(255) NOT NULL,
    description     TEXT,
    action_url      TEXT,
    is_dismissed    BOOLEAN NOT NULL DEFAULT FALSE,
    dismissed_by_id UUID REFERENCES staff(id),
    dismissed_at    TIMESTAMPTZ,
    expires_at      TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_alerts_school ON alerts(school_id, is_dismissed, created_at DESC);
CREATE INDEX idx_alerts_student ON alerts(student_id, created_at DESC);

-- ============================================================
-- WHATSAPP INBOX MESSAGES (gap analysis §5.6)
-- ============================================================
CREATE TABLE whatsapp_inbox_messages (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id       UUID NOT NULL REFERENCES schools(id),
    from_phone      VARCHAR(15) NOT NULL,
    parent_id       UUID REFERENCES parents(id),
    student_id      UUID REFERENCES students(id),
    routed_to_id    UUID REFERENCES staff(id),
    message_body    TEXT NOT NULL,
    wa_message_id   VARCHAR(100),
    is_read         BOOLEAN NOT NULL DEFAULT FALSE,
    is_resolved     BOOLEAN NOT NULL DEFAULT FALSE,
    received_at     TIMESTAMPTZ NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_wa_inbox_school
    ON whatsapp_inbox_messages(school_id, is_resolved, received_at DESC);
CREATE INDEX idx_wa_inbox_teacher
    ON whatsapp_inbox_messages(routed_to_id, is_read, received_at DESC);

-- ============================================================
-- FEE REMINDER SCHEDULES (gap analysis §5.3)
-- ============================================================
CREATE TABLE fee_reminder_schedules (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id        UUID NOT NULL REFERENCES schools(id),
    name             VARCHAR(100) NOT NULL,
    trigger_type     VARCHAR(10) NOT NULL,
    days_offset      INTEGER NOT NULL,
    include_upi_link BOOLEAN NOT NULL DEFAULT TRUE,
    is_active        BOOLEAN NOT NULL DEFAULT TRUE,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE(school_id, trigger_type, days_offset)
);
CREATE INDEX idx_fee_schedules_school ON fee_reminder_schedules(school_id, is_active);

-- ============================================================
-- SUBSTITUTE TEACHER ASSIGNMENTS (gap analysis §5.5)
-- ============================================================
CREATE TABLE substitute_assignments (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id           UUID NOT NULL REFERENCES schools(id),
    absent_teacher_id   UUID NOT NULL REFERENCES staff(id),
    substitute_id       UUID NOT NULL REFERENCES staff(id),
    section_id          UUID NOT NULL REFERENCES sections(id),
    assigned_date       DATE NOT NULL,
    note                TEXT,
    created_by_id       UUID REFERENCES staff(id),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE(section_id, assigned_date)
);
CREATE INDEX idx_sub_assign_school ON substitute_assignments(school_id, assigned_date DESC);
CREATE INDEX idx_sub_assign_teacher ON substitute_assignments(substitute_id, assigned_date DESC);


-- ─── from: V2__auth_identifier_uniqueness.sql ───
-- ============================================================
-- V2: Auth identifier uniqueness + email-based signup support
-- Phone and email are both login identifiers — either can authenticate a staff member — and
-- each must be globally unique across all tenants.
-- Phone is made nullable so an EMAIL-only signup can land without a phone number.
-- ============================================================

-- Make phone nullable to allow email-only signup (feature flag: app.signup.channel=EMAIL)
ALTER TABLE schools ALTER COLUMN phone DROP NOT NULL;
ALTER TABLE staff   ALTER COLUMN phone DROP NOT NULL;

-- Globally unique phone for staff (login identifier; partial on NOT NULL for email-only staff)
CREATE UNIQUE INDEX uq_staff_phone_global ON staff(phone) WHERE phone IS NOT NULL;

-- Emails are globally unique when present (login identifier)
CREATE UNIQUE INDEX uq_staff_email   ON staff(email)   WHERE email IS NOT NULL;
CREATE UNIQUE INDEX uq_schools_email ON schools(email) WHERE email IS NOT NULL;


-- ─── from: V3__notification_webhook_correlation.sql ───
-- ============================================================
-- V3: Webhook correlation columns
-- Slice 6.5: fast lookup of inbound messages by parent phone + resolving a delivery
--           status update from a BSP requires an index on wa_message_id across tenants.
-- Slice 7.5: payment webhook → FeePayment auto-create needs an idempotency guarantee.
--           A second webhook delivery for the same provider_reference must be a no-op
--           rather than a duplicate row (BSPs retry aggressively on 5xx).
-- ============================================================

-- Slice 7.5: record of the upstream provider payment reference (Stripe session id /
-- Razorpay payment_link id). Unique when present — the webhook idempotency key.
ALTER TABLE fee_payments
    ADD COLUMN IF NOT EXISTS provider_reference VARCHAR(100);

CREATE UNIQUE INDEX IF NOT EXISTS uq_fee_payments_provider_reference
    ON fee_payments(provider_reference)
    WHERE provider_reference IS NOT NULL;

-- Slice 6.5: cross-tenant wa_message_id lookup already exists in V1 as a partial index —
-- no change needed. Here we just add a targeted index on recipient_phone so the 2-way
-- inbox can find "the last message we sent this phone" quickly for threading.
CREATE INDEX IF NOT EXISTS idx_notif_recipient_phone
    ON notification_log(recipient_phone, created_at DESC);


-- ─── from: V4__student_risk_scores.sql ───
-- ============================================================
-- V4: At-risk student scoring (Slice 10b)
-- One row per student, upserted weekly by AtRiskDetectionService. The composite score
-- blends attendance, fee, and exam trend into a single 0-100 number the dashboard can sort by.
-- Historical tracking is deferred — for now "latest state" is what dashboards need. When we
-- need month-over-month charts we'll add a student_risk_scores_history table alongside.
-- ============================================================

CREATE TABLE student_risk_scores (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id               UUID NOT NULL REFERENCES schools(id),
    student_id              UUID NOT NULL REFERENCES students(id),
    -- Composite 0–100; higher = more at risk.
    score                   INTEGER NOT NULL,
    attendance_pct          NUMERIC(5,2),
    fee_outstanding_paise   BIGINT NOT NULL DEFAULT 0,
    marks_trend             VARCHAR(10),  -- UP | DOWN | FLAT | UNKNOWN
    top_factor              VARCHAR(40),  -- ATTENDANCE | FEE | MARKS
    calculated_at           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE(school_id, student_id)
);

CREATE INDEX idx_risk_scores_school_score
    ON student_risk_scores(school_id, score DESC);


-- ─── from: V5__student_documents.sql ───
-- ============================================================
-- V5: Student documents (Slice 3 continuation / gap analysis §5.1)
-- Uploaded paperwork (admission form, birth cert, TC, photo, medical, other) kept per
-- student. Storage backend is the same FileStorageService abstraction used for receipts —
-- this table just records the key + metadata so documents can be listed/downloaded.
-- ============================================================

CREATE TABLE student_documents (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id       UUID NOT NULL REFERENCES schools(id),
    student_id      UUID NOT NULL REFERENCES students(id),
    doc_type        VARCHAR(30) NOT NULL,
    storage_key     TEXT NOT NULL,
    file_url        TEXT NOT NULL,
    file_name       VARCHAR(255),
    content_type    VARCHAR(100),
    size_bytes      BIGINT,
    uploaded_by_id  UUID REFERENCES staff(id),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_student_documents_student
    ON student_documents(student_id, created_at DESC);
CREATE INDEX idx_student_documents_school
    ON student_documents(school_id, created_at DESC);


-- ─── from: V6__saas_core.sql ───
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


-- ─── from: V7__tenant_provider_configs.sql ───
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


-- ─── from: V8__idempotency_keys.sql ───
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


-- ─── from: V9__timetable.sql ───
-- =====================================================================================
-- V9 — Timetable module (gap analysis §5.5)
-- =====================================================================================

-- Period definitions per school: ordered time slots within a day. School-wide; sections
-- can share or diverge via per-section entries that simply use the same period.
CREATE TABLE timetable_periods (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id       UUID NOT NULL REFERENCES schools(id) ON DELETE CASCADE,
    name            VARCHAR(40) NOT NULL,             -- "Period 1", "Lunch", "Assembly"
    start_time      TIME NOT NULL,
    end_time        TIME NOT NULL,
    sort_order      INTEGER NOT NULL DEFAULT 0,
    is_break        BOOLEAN NOT NULL DEFAULT FALSE,    -- break/lunch slots aren't taught
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE(school_id, name)
);
CREATE INDEX idx_timetable_periods_school ON timetable_periods(school_id, sort_order);

-- One row per (section, day_of_week, period). day_of_week 1=Mon … 7=Sun (ISO).
CREATE TABLE timetable_entries (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id       UUID NOT NULL REFERENCES schools(id) ON DELETE CASCADE,
    section_id      UUID NOT NULL REFERENCES sections(id) ON DELETE CASCADE,
    period_id       UUID NOT NULL REFERENCES timetable_periods(id) ON DELETE CASCADE,
    day_of_week     INTEGER NOT NULL,                  -- 1..7
    subject_id      UUID REFERENCES subjects(id),       -- nullable for free periods
    teacher_id      UUID REFERENCES staff(id),
    note            TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE(section_id, day_of_week, period_id),
    CHECK (day_of_week BETWEEN 1 AND 7)
);
CREATE INDEX idx_timetable_entries_section ON timetable_entries(section_id, day_of_week);
CREATE INDEX idx_timetable_entries_teacher ON timetable_entries(teacher_id, day_of_week);


-- ─── from: V10__homework.sql ───
-- =====================================================================================
-- V10 — Homework / Assignments
-- =====================================================================================

CREATE TABLE homework_assignments (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id       UUID NOT NULL REFERENCES schools(id) ON DELETE CASCADE,
    section_id      UUID NOT NULL REFERENCES sections(id) ON DELETE CASCADE,
    subject_id      UUID REFERENCES subjects(id),
    title           VARCHAR(255) NOT NULL,
    body            TEXT NOT NULL,
    attachment_url  TEXT,
    due_date        DATE,
    created_by_id   UUID REFERENCES staff(id),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_homework_section ON homework_assignments(section_id, due_date);
CREATE INDEX idx_homework_school_date ON homework_assignments(school_id, created_at DESC);

CREATE TABLE homework_submissions (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id           UUID NOT NULL REFERENCES schools(id) ON DELETE CASCADE,
    assignment_id       UUID NOT NULL REFERENCES homework_assignments(id) ON DELETE CASCADE,
    student_id          UUID NOT NULL REFERENCES students(id) ON DELETE CASCADE,
    submitted_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    submission_text     TEXT,
    attachment_url      TEXT,
    teacher_remark      TEXT,
    grade               VARCHAR(10),                -- optional letter / number grade
    graded_at           TIMESTAMPTZ,
    UNIQUE(assignment_id, student_id)
);
CREATE INDEX idx_hw_submissions_student ON homework_submissions(student_id, submitted_at DESC);


-- ─── from: V11__library.sql ───
-- =====================================================================================
-- V11 — Library module
-- =====================================================================================

CREATE TABLE library_books (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id       UUID NOT NULL REFERENCES schools(id) ON DELETE CASCADE,
    title           VARCHAR(255) NOT NULL,
    author          VARCHAR(255),
    isbn            VARCHAR(20),
    publisher       VARCHAR(255),
    category        VARCHAR(80),
    -- Total physical copies; available_copies decremented on issue, incremented on return.
    total_copies    INTEGER NOT NULL DEFAULT 1,
    available_copies INTEGER NOT NULL DEFAULT 1,
    is_active       BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by_id   UUID,
    UNIQUE(school_id, isbn)
);
CREATE INDEX idx_library_books_school ON library_books(school_id, is_active);
CREATE INDEX idx_library_books_title_trgm ON library_books USING GIN (title gin_trgm_ops);

CREATE TABLE library_issues (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id       UUID NOT NULL REFERENCES schools(id) ON DELETE CASCADE,
    book_id         UUID NOT NULL REFERENCES library_books(id),
    student_id      UUID NOT NULL REFERENCES students(id),
    issued_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    due_date        DATE NOT NULL,
    returned_at     TIMESTAMPTZ,
    fine_paise      BIGINT NOT NULL DEFAULT 0,         -- per-day overdue fine, computed on return
    issued_by_id    UUID,
    note            TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by_id   UUID
);
CREATE INDEX idx_library_issues_student ON library_issues(student_id, issued_at DESC);
CREATE INDEX idx_library_issues_outstanding ON library_issues(school_id, returned_at)
    WHERE returned_at IS NULL;


-- ─── from: V12__transport.sql ───
-- =====================================================================================
-- V12 — Transport module
-- =====================================================================================

CREATE TABLE transport_routes (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id       UUID NOT NULL REFERENCES schools(id) ON DELETE CASCADE,
    name            VARCHAR(120) NOT NULL,        -- "Route A — North Pune"
    -- Stops as ordered JSONB: [{"order":1,"name":"Aundh","time":"07:30"}, ...]
    stops           JSONB NOT NULL DEFAULT '[]'::jsonb,
    fare_paise      BIGINT NOT NULL DEFAULT 0,    -- monthly fare for the route
    is_active       BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by_id   UUID,
    UNIQUE(school_id, name)
);
CREATE INDEX idx_transport_routes_school ON transport_routes(school_id, is_active);

CREATE TABLE transport_vehicles (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id           UUID NOT NULL REFERENCES schools(id) ON DELETE CASCADE,
    registration_no     VARCHAR(40) NOT NULL,        -- "MH 12 AB 1234"
    driver_staff_id     UUID REFERENCES staff(id),
    capacity            INTEGER NOT NULL DEFAULT 30,
    route_id            UUID REFERENCES transport_routes(id),
    is_active           BOOLEAN NOT NULL DEFAULT TRUE,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by_id       UUID,
    UNIQUE(school_id, registration_no)
);
CREATE INDEX idx_transport_vehicles_school ON transport_vehicles(school_id, is_active);
CREATE INDEX idx_transport_vehicles_route ON transport_vehicles(route_id);

CREATE TABLE student_transport_assignments (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id       UUID NOT NULL REFERENCES schools(id) ON DELETE CASCADE,
    student_id      UUID NOT NULL REFERENCES students(id) ON DELETE CASCADE,
    route_id        UUID NOT NULL REFERENCES transport_routes(id),
    stop_name       VARCHAR(120),                  -- which stop on the route
    start_date      DATE NOT NULL,
    end_date        DATE,                           -- null = ongoing
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by_id   UUID,
    -- One ongoing assignment per student (enforced by app, not DDL — students may have a
    -- historic ended assignment alongside a new active one).
    UNIQUE(student_id, start_date)
);
CREATE INDEX idx_sta_student ON student_transport_assignments(student_id, end_date);
CREATE INDEX idx_sta_route ON student_transport_assignments(route_id, end_date);


-- ─── from: V13__outbox.sql ───
-- =====================================================================================
-- V13 — Transactional outbox for durable async events
--
-- Pattern: when a business write needs to publish an event, insert an outbox_events row in
-- the SAME transaction. A poller (Spring @Scheduled, every 5s) reads unprocessed rows and
-- republishes them via Spring ApplicationEventPublisher — the existing async listeners
-- (AbsenceAlertService, ReceiptDeliveryListener, ReportCardDeliveryListener, etc.) keep
-- receiving them unchanged.
--
-- Why: today's @Async listeners survive a JVM restart only if the listener completed
-- before the restart. An attendance submission followed by an immediate crash loses the
-- queued WhatsApp absence alerts. With the outbox they're replayed on restart.
-- =====================================================================================

CREATE TABLE outbox_events (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    -- school_id is nullable for platform-level events (e.g. cross-tenant billing webhook
    -- transitions). For tenant-scoped events, set it for traceability + filtered queries.
    school_id        UUID,
    topic            VARCHAR(120) NOT NULL,    -- fully-qualified event class name, e.g.
                                                -- "in.schoolapp.attendance.event.AttendanceSubmittedEvent"
    payload          JSONB NOT NULL,            -- the event object as JSON
    attempts         INTEGER NOT NULL DEFAULT 0,
    last_error       TEXT,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    -- Next time the poller is allowed to pick up this row. Set to created_at on insert;
    -- the poller updates it to NOW() + backoff on failure for exponential retry.
    next_attempt_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    -- Stamped once the event has been republished successfully.
    processed_at     TIMESTAMPTZ
);

-- Polling index — only rows still pending. WHERE-clause keeps the index tiny.
CREATE INDEX idx_outbox_pending ON outbox_events(next_attempt_at)
    WHERE processed_at IS NULL;

CREATE INDEX idx_outbox_school_created ON outbox_events(school_id, created_at DESC);


-- ─── from: V14__slice12_pdf_and_feature_expansion.sql ───
-- =====================================================================================
-- Slice 12 — PDF generation pipeline + broad feature expansion
-- =====================================================================================
-- Adds:
--   1. document_templates: per-school HTML override for any document type. Without a row,
--      the renderer falls back to the classpath template shipped with the JAR.
--   2. The full set of new feature_keys introduced by Slices 12-21. Defaults are off; the
--      ENTERPRISE plan gets them all, others get a curated subset.
-- =====================================================================================

CREATE TABLE document_templates (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id       UUID NOT NULL REFERENCES schools(id) ON DELETE CASCADE,
    -- Identifier matches DocumentType enum values: RECEIPT, TC, BONAFIDE, HALL_TICKET, REPORT_CARD, etc.
    document_type   VARCHAR(40) NOT NULL,
    -- Thymeleaf HTML fragment. Renderer resolves model variables (school, student, fee, exam).
    html_template   TEXT NOT NULL,
    -- Optional CSS override; concatenated with default stylesheet.
    css_override    TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by_id   UUID,
    UNIQUE(school_id, document_type)
);
CREATE INDEX idx_document_templates_school ON document_templates(school_id);

-- =====================================================================================
-- New feature keys covering Slices 12-21. Kept screaming-snake; never rename.
-- =====================================================================================
INSERT INTO features (feature_key, name, description, default_enabled, category) VALUES
    -- Slice 12: PDF generation
    ('PDF_GENERATION',      'PDF generation',                'Server-side PDF rendering for receipts, TCs, hall tickets, report cards', FALSE, 'Documents'),
    ('TRANSFER_CERTIFICATE','Transfer certificate',          'Generate TC PDF on student exit',                                          FALSE, 'Documents'),
    ('BONAFIDE_CERTIFICATE','Bonafide certificate',          'One-click bonafide PDF for current students',                              FALSE, 'Documents'),
    ('HALL_TICKETS',        'Hall tickets',                  'Bulk-generate exam admit cards per section',                              FALSE, 'Documents'),

    -- Slice 13: Bulk import
    ('BULK_IMPORT',         'Bulk CSV import',               'Excel/CSV upload for students, staff, fee structures',                    FALSE, 'Onboarding'),

    -- Slice 14: Notification expansion
    ('SMS_FALLBACK',        'SMS fallback',                  'Send via SMS when WhatsApp delivery fails or parent opted out',           FALSE, 'Communication'),
    ('PUSH_NOTIFICATIONS',  'Push notifications',            'Mobile push via FCM / APNs',                                              FALSE, 'Communication'),
    ('TRANSLATION',         'Auto-translation',              'Translate circulars + comments per parent language preference',          FALSE, 'Communication'),
    ('VOICE_CALLS',         'Voice / video calls',           'In-app audio / video sessions',                                            FALSE, 'Communication'),

    -- Slice 15: Fee depth
    ('LATE_FEE_AUTOMATION', 'Late fee automation',           'Auto-apply late fee N days after due date',                               FALSE, 'Finance'),
    ('FEE_DISCOUNTS',       'Discounts & concessions',       'Sibling discount, merit scholarship, financial-aid waivers',              FALSE, 'Finance'),
    ('FEE_REFUNDS',         'Refunds & adjustments',         'Refund + ledger adjustments with audit trail',                            FALSE, 'Finance'),
    ('FEE_INSTALLMENTS',    'Multi-installment plans',       'Split a fee head into N installments with separate due dates',            FALSE, 'Finance'),
    ('FEE_GST',             'GST on fee heads',              'Per-head GST flag + GSTIN-tagged receipt',                                FALSE, 'Finance'),

    -- Slice 16: HR
    ('STAFF_ATTENDANCE',    'Staff attendance',              'Mark / view staff attendance separately from students',                  FALSE, 'HR'),
    ('LEAVE_MANAGEMENT',    'Leave management',              'Leave applications + approval workflow',                                  FALSE, 'HR'),
    ('PAYROLL',             'Payroll',                       'Salary structure, payslip generation, PF/ESI calc',                       FALSE, 'HR'),

    -- Slice 17: Admissions
    ('ADMISSIONS_FUNNEL',   'Admissions funnel',             'Public enquiry → application → test → offer → enrolled',                  FALSE, 'Admissions'),

    -- Slice 18: Auxiliary modules
    ('HOSTEL',              'Hostel / boarding',             'Room allocation, warden, visitor logs, mess plans',                       FALSE, 'Boarding'),
    ('CAFETERIA',           'Cafeteria',                     'Meal plans, pre-paid card / wallet, daily menu',                          FALSE, 'Boarding'),
    ('INVENTORY',           'Inventory / assets',            'Asset register, issue/return, maintenance, depreciation',                 FALSE, 'Operations'),

    -- Slice 19: AI
    ('AI_RISK_SCORING',     'AI risk scoring',               'Attendance + marks + behavior → at-risk student alerts',                  FALSE, 'AI'),
    ('AI_CHATBOT',          'AI parent chatbot',             'LLM-backed FAQ for parents (PTM dates, fee balance, etc.)',               FALSE, 'AI'),
    ('AI_AUTO_GRADE',       'AI answer-sheet grading',       'OCR + LLM scoring of scanned handwritten answer sheets',                 FALSE, 'AI'),

    -- Slice 20: Govt integrations (India)
    ('UDISE_EXPORT',        'UDISE+ export',                 'Annual govt-mandated school data export',                                 FALSE, 'Compliance'),
    ('DIGILOCKER_PUSH',     'DigiLocker push',               'Push TCs / marksheets to student DigiLocker',                             FALSE, 'Compliance'),
    ('DIKSHA_SYNC',         'DIKSHA content sync',           'NCERT DIKSHA learning resources for teachers',                            FALSE, 'Compliance'),
    ('NAD_INTEGRATION',     'NAD integration',               'National Academic Depository certificate registration',                   FALSE, 'Compliance'),

    -- Slice 21: Power features
    ('AUTO_TIMETABLE',      'Auto-timetable generator',      'Constraint-solver based weekly timetable',                                FALSE, 'Operations'),
    ('PERIOD_ATTENDANCE',   'Period-wise attendance',        'Per-period attendance for subject teachers',                              FALSE, 'Operations'),

    -- Extra ops
    ('BIOMETRIC_DEVICES',   'Biometric / RFID devices',      'Sync with attendance hardware (ESSL, Realtime)',                          FALSE, 'Integrations'),
    ('FACE_RECOGNITION',    'Face recognition attendance',   'Camera-based attendance via face match',                                  FALSE, 'AI'),
    ('GPS_TRANSPORT',       'Live bus GPS',                  'Real-time bus location + ETA push to parents',                            FALSE, 'Transport'),

    -- Document vault (Slice 12 boundary)
    ('DOCUMENT_VAULT',      'Student document vault',        'Per-student secured file vault for Aadhaar, birth cert, TC, photo',       FALSE, 'Documents')
ON CONFLICT (feature_key) DO NOTHING;

-- Bundle the new features into plans. Logic:
--   - Documents (RECEIPT-class)            → STARTER+ (every paid plan gets PDFs)
--   - Bulk import                          → STARTER+
--   - Notification expansion (SMS, Push)   → GROWTH+
--   - Fee depth (LATE_FEE, DISCOUNTS, etc) → GROWTH+
--   - HR module                            → GROWTH+
--   - Admissions, Hostel, Inventory        → ENTERPRISE
--   - AI features                          → ENTERPRISE (or per-tenant override on lower plans)
--   - Govt integrations                    → ENTERPRISE
--   - Power features (timetable solver)    → ENTERPRISE
WITH p AS (SELECT id, code FROM plans)
INSERT INTO plan_features (plan_id, feature_key)
SELECT p.id, f.feature_key
FROM p
CROSS JOIN LATERAL (VALUES
    -- STARTER+ : core documents
    ('STARTER',    'PDF_GENERATION'),
    ('STARTER',    'TRANSFER_CERTIFICATE'),
    ('STARTER',    'BONAFIDE_CERTIFICATE'),
    ('STARTER',    'HALL_TICKETS'),
    ('STARTER',    'BULK_IMPORT'),
    ('STARTER',    'DOCUMENT_VAULT'),

    -- GROWTH+ : richer comms + fee depth + HR
    ('GROWTH',     'PDF_GENERATION'),
    ('GROWTH',     'TRANSFER_CERTIFICATE'),
    ('GROWTH',     'BONAFIDE_CERTIFICATE'),
    ('GROWTH',     'HALL_TICKETS'),
    ('GROWTH',     'BULK_IMPORT'),
    ('GROWTH',     'DOCUMENT_VAULT'),
    ('GROWTH',     'SMS_FALLBACK'),
    ('GROWTH',     'PUSH_NOTIFICATIONS'),
    ('GROWTH',     'TRANSLATION'),
    ('GROWTH',     'LATE_FEE_AUTOMATION'),
    ('GROWTH',     'FEE_DISCOUNTS'),
    ('GROWTH',     'FEE_REFUNDS'),
    ('GROWTH',     'FEE_INSTALLMENTS'),
    ('GROWTH',     'FEE_GST'),
    ('GROWTH',     'STAFF_ATTENDANCE'),
    ('GROWTH',     'LEAVE_MANAGEMENT'),
    ('GROWTH',     'PERIOD_ATTENDANCE'),

    -- ENTERPRISE : everything
    ('ENTERPRISE', 'PDF_GENERATION'),
    ('ENTERPRISE', 'TRANSFER_CERTIFICATE'),
    ('ENTERPRISE', 'BONAFIDE_CERTIFICATE'),
    ('ENTERPRISE', 'HALL_TICKETS'),
    ('ENTERPRISE', 'BULK_IMPORT'),
    ('ENTERPRISE', 'DOCUMENT_VAULT'),
    ('ENTERPRISE', 'SMS_FALLBACK'),
    ('ENTERPRISE', 'PUSH_NOTIFICATIONS'),
    ('ENTERPRISE', 'TRANSLATION'),
    ('ENTERPRISE', 'VOICE_CALLS'),
    ('ENTERPRISE', 'LATE_FEE_AUTOMATION'),
    ('ENTERPRISE', 'FEE_DISCOUNTS'),
    ('ENTERPRISE', 'FEE_REFUNDS'),
    ('ENTERPRISE', 'FEE_INSTALLMENTS'),
    ('ENTERPRISE', 'FEE_GST'),
    ('ENTERPRISE', 'STAFF_ATTENDANCE'),
    ('ENTERPRISE', 'LEAVE_MANAGEMENT'),
    ('ENTERPRISE', 'PAYROLL'),
    ('ENTERPRISE', 'ADMISSIONS_FUNNEL'),
    ('ENTERPRISE', 'HOSTEL'),
    ('ENTERPRISE', 'CAFETERIA'),
    ('ENTERPRISE', 'INVENTORY'),
    ('ENTERPRISE', 'AI_RISK_SCORING'),
    ('ENTERPRISE', 'AI_CHATBOT'),
    ('ENTERPRISE', 'AI_AUTO_GRADE'),
    ('ENTERPRISE', 'UDISE_EXPORT'),
    ('ENTERPRISE', 'DIGILOCKER_PUSH'),
    ('ENTERPRISE', 'DIKSHA_SYNC'),
    ('ENTERPRISE', 'NAD_INTEGRATION'),
    ('ENTERPRISE', 'AUTO_TIMETABLE'),
    ('ENTERPRISE', 'PERIOD_ATTENDANCE'),
    ('ENTERPRISE', 'BIOMETRIC_DEVICES'),
    ('ENTERPRISE', 'FACE_RECOGNITION'),
    ('ENTERPRISE', 'GPS_TRANSPORT')
) AS f(plan_code, feature_key)
WHERE p.code = f.plan_code
ON CONFLICT DO NOTHING;


-- ─── from: V15__slice15_fee_depth.sql ───
-- =====================================================================================
-- Slice 15 — Fee depth: late-fee automation, discounts/concessions, refunds/adjustments,
-- multi-installment plans, GST per fee head.
-- =====================================================================================
-- Design notes:
--   1. All money values stay in PAISE (BIGINT). Never use DOUBLE for currency.
--   2. fee_adjustments is the audit ledger — every late_fee, discount-applied, refund,
--      manual write-off lands here. Invoices snapshot their state by referencing the
--      head adjustment ids, so /payments/{id}/refund preserves history.
--   3. Per-head LATE_FEE config is intentionally on fee_heads (not a global setting):
--      tuition fee can have ₹10/day cap ₹500, transport can have ₹0 (no late fee).
-- =====================================================================================

-- ---------- 1. fee_discounts ----------
-- Tracks structural concessions (sibling discount, merit scholarship, financial-aid waiver).
-- Applied to a specific student over a date range. The invoice-generation service consults
-- this table at compute time; no row mutations on invoices themselves.
CREATE TABLE fee_discounts (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id        UUID NOT NULL REFERENCES schools(id) ON DELETE CASCADE,
    student_id       UUID NOT NULL REFERENCES students(id) ON DELETE CASCADE,
    fee_head_id      UUID REFERENCES fee_heads(id) ON DELETE CASCADE,
    -- NULL means the discount applies to ALL heads (e.g. blanket sibling discount).
    discount_type    VARCHAR(30) NOT NULL,
    -- SIBLING | SCHOLARSHIP | FINANCIAL_AID | STAFF_KID | EARLY_BIRD | CUSTOM
    percent          NUMERIC(5,2),                -- e.g. 10.00 = 10%
    fixed_paise      BIGINT,                      -- alternative to percent; mutually exclusive
    valid_from       DATE NOT NULL,
    valid_until      DATE,                        -- NULL = open-ended
    reason           TEXT,
    approved_by_id   UUID,                        -- staff who approved
    active           BOOLEAN NOT NULL DEFAULT TRUE,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by_id    UUID,
    CONSTRAINT fee_discount_amount_xor CHECK (
        (percent IS NOT NULL AND fixed_paise IS NULL)
        OR (percent IS NULL AND fixed_paise IS NOT NULL)
    )
);
CREATE INDEX idx_fee_discounts_school ON fee_discounts(school_id);
CREATE INDEX idx_fee_discounts_student ON fee_discounts(student_id) WHERE active;


-- ---------- 2. fee_adjustments ----------
-- Append-only audit ledger of every non-payment mutation to an invoice.
CREATE TABLE fee_adjustments (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id        UUID NOT NULL REFERENCES schools(id) ON DELETE CASCADE,
    invoice_id       UUID REFERENCES fee_invoices(id) ON DELETE CASCADE,
    payment_id       UUID REFERENCES fee_payments(id) ON DELETE SET NULL,
    -- LATE_FEE | DISCOUNT_APPLIED | REFUND | WRITE_OFF | MANUAL_CREDIT | MANUAL_DEBIT
    adjustment_type  VARCHAR(30) NOT NULL,
    amount_paise     BIGINT NOT NULL,             -- positive: increases due; negative: reduces
    reason           TEXT,
    approved_by_id   UUID,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by_id    UUID
);
CREATE INDEX idx_fee_adjustments_school ON fee_adjustments(school_id);
CREATE INDEX idx_fee_adjustments_invoice ON fee_adjustments(invoice_id);
CREATE INDEX idx_fee_adjustments_payment ON fee_adjustments(payment_id);


-- ---------- 3. fee_installment_plans / fee_installments ----------
-- An installment plan supersedes a parent invoice with N child invoices, each with its own
-- due date. The parent's status flips to SUPERSEDED so the dashboard / defaulters list
-- ignores it (queries already filter by status).
CREATE TABLE fee_installment_plans (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id        UUID NOT NULL REFERENCES schools(id) ON DELETE CASCADE,
    parent_invoice_id UUID NOT NULL REFERENCES fee_invoices(id) ON DELETE CASCADE,
    installment_count INT NOT NULL,
    notes            TEXT,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by_id    UUID,
    UNIQUE(parent_invoice_id)
);

CREATE TABLE fee_installments (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    plan_id          UUID NOT NULL REFERENCES fee_installment_plans(id) ON DELETE CASCADE,
    child_invoice_id UUID NOT NULL REFERENCES fee_invoices(id) ON DELETE CASCADE,
    sequence_no      INT NOT NULL,                -- 1, 2, 3 …
    due_date         DATE NOT NULL,
    amount_paise     BIGINT NOT NULL,
    UNIQUE(plan_id, sequence_no)
);


-- ---------- 4. Late-fee configuration on fee_heads ----------
ALTER TABLE fee_heads
    ADD COLUMN gst_percent              NUMERIC(5,2) NOT NULL DEFAULT 0,
    ADD COLUMN late_fee_paise_per_day   BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN late_fee_grace_days      INT NOT NULL DEFAULT 0,
    ADD COLUMN late_fee_cap_paise       BIGINT;     -- NULL = uncapped


-- ---------- 5. Mutations on fee_invoices ----------
ALTER TABLE fee_invoices
    ADD COLUMN late_fee_applied_paise   BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN last_late_fee_applied_at TIMESTAMPTZ,
    ADD COLUMN discount_applied_paise   BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN gst_paise                BIGINT NOT NULL DEFAULT 0,
    -- New status values: SUPERSEDED (parent of an installment plan), REFUNDED (everything reversed).
    -- The enum is stored as VARCHAR in the existing schema (see V1), so no enum migration needed —
    -- just remember to update InvoiceStatus.java in lock-step.
    ADD COLUMN superseded_by_plan_id    UUID REFERENCES fee_installment_plans(id) ON DELETE SET NULL;


-- ---------- 6. Per-school GSTIN ----------
-- Stored in schools.settings JSONB under key "gstin" so we don't need a new column.
-- Receipts read it via DocumentService.

-- =====================================================================================
-- No new feature_keys here — Slice 12's V14 already seeded LATE_FEE_AUTOMATION,
-- FEE_DISCOUNTS, FEE_REFUNDS, FEE_INSTALLMENTS, FEE_GST. The plan mappings are in V14.
-- =====================================================================================


-- ─── from: V16__slice16_hr.sql ───
-- =====================================================================================
-- Slice 16 — HR module: staff attendance, leave management, payroll
-- =====================================================================================
-- Design notes:
--   1. staff_attendance is per-day per-staff (unique). Bulk marking is N rows in one txn.
--   2. leave_applications is the source of truth for "is staff X on approved leave on date Y"
--      — the payroll calc joins this to deduct LWP days from the gross.
--   3. salary_structures supports per-role baselines + per-staff overrides via the
--      staff_id column (NULL = role default, NOT NULL = staff-specific).
--   4. Payslips are immutable snapshots — once generated, schema does not allow updates
--      (enforced in service; no trigger needed).
-- =====================================================================================

-- ---------- staff_attendance ----------
CREATE TABLE staff_attendance (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id       UUID NOT NULL REFERENCES schools(id) ON DELETE CASCADE,
    staff_id        UUID NOT NULL REFERENCES staff(id) ON DELETE CASCADE,
    attendance_date DATE NOT NULL,
    status          VARCHAR(20) NOT NULL,                -- PRESENT | ABSENT | HALF_DAY | LEAVE | HOLIDAY | LATE
    notes           TEXT,
    marked_by_id    UUID,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE(school_id, staff_id, attendance_date)
);
CREATE INDEX idx_staff_attendance_date  ON staff_attendance(school_id, attendance_date);
CREATE INDEX idx_staff_attendance_staff ON staff_attendance(staff_id);


-- ---------- leave_applications ----------
CREATE TABLE leave_applications (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id       UUID NOT NULL REFERENCES schools(id) ON DELETE CASCADE,
    staff_id        UUID NOT NULL REFERENCES staff(id) ON DELETE CASCADE,
    -- CASUAL | SICK | EARNED | UNPAID | MATERNITY | PATERNITY | COMP_OFF | OTHER
    leave_type      VARCHAR(20) NOT NULL,
    start_date      DATE NOT NULL,
    end_date        DATE NOT NULL,
    -- Floating-point days handles half-day applications (0.5, 1.5 etc).
    days            NUMERIC(5,2) NOT NULL,
    reason          TEXT,
    -- SUBMITTED → APPROVED | REJECTED | CANCELLED
    status          VARCHAR(15) NOT NULL DEFAULT 'SUBMITTED',
    decided_by_id   UUID,
    decided_at      TIMESTAMPTZ,
    decision_note   TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by_id   UUID,
    CONSTRAINT leave_dates_valid CHECK (end_date >= start_date),
    CONSTRAINT leave_days_positive CHECK (days > 0)
);
CREATE INDEX idx_leave_app_staff  ON leave_applications(staff_id, start_date);
CREATE INDEX idx_leave_app_school ON leave_applications(school_id, status);


-- ---------- leave_balances ----------
-- Per-staff, per-leave-type entitlement and consumption. The HR admin maintains this row.
-- LeaveService.approve() decrements `consumed_days`. A negative balance is allowed (carry-over
-- + unpaid) — service surfaces it as a warning, not a hard error.
CREATE TABLE leave_balances (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id       UUID NOT NULL REFERENCES schools(id) ON DELETE CASCADE,
    staff_id        UUID NOT NULL REFERENCES staff(id) ON DELETE CASCADE,
    leave_type      VARCHAR(20) NOT NULL,
    year            INT NOT NULL,                        -- e.g. 2026
    entitled_days   NUMERIC(5,2) NOT NULL,
    consumed_days   NUMERIC(5,2) NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE(school_id, staff_id, leave_type, year)
);


-- ---------- salary_structures ----------
-- Either role-default (staff_id NULL) OR staff-specific (staff_id set). When generating
-- a payslip, the service looks for the staff-specific row first, falling back to role.
CREATE TABLE salary_structures (
    id                       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id                UUID NOT NULL REFERENCES schools(id) ON DELETE CASCADE,
    role                     VARCHAR(30) NOT NULL,        -- StaffRole enum value
    staff_id                 UUID REFERENCES staff(id) ON DELETE CASCADE,
    basic_paise              BIGINT NOT NULL DEFAULT 0,
    hra_paise                BIGINT NOT NULL DEFAULT 0,    -- house rent allowance
    da_paise                 BIGINT NOT NULL DEFAULT 0,    -- dearness allowance
    special_allowance_paise  BIGINT NOT NULL DEFAULT 0,
    other_allowance_paise    BIGINT NOT NULL DEFAULT 0,
    pf_percent               NUMERIC(5,2) NOT NULL DEFAULT 12.00,   -- typical India default
    esi_percent              NUMERIC(5,2) NOT NULL DEFAULT 0.75,
    professional_tax_paise   BIGINT NOT NULL DEFAULT 0,    -- per-state flat amount
    notes                    TEXT,
    effective_from           DATE NOT NULL,
    effective_until          DATE,                         -- NULL = open-ended
    active                   BOOLEAN NOT NULL DEFAULT TRUE,
    created_at               TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at               TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by_id            UUID
);
CREATE INDEX idx_salary_struct_school ON salary_structures(school_id);
CREATE INDEX idx_salary_struct_staff  ON salary_structures(staff_id) WHERE staff_id IS NOT NULL;
CREATE INDEX idx_salary_struct_role   ON salary_structures(school_id, role) WHERE staff_id IS NULL;


-- ---------- payslips ----------
-- Immutable monthly snapshots — once a payslip is generated, the row is never updated.
-- Re-generation creates a new row with version+1 (used for corrections / re-issues).
CREATE TABLE payslips (
    id                       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id                UUID NOT NULL REFERENCES schools(id) ON DELETE CASCADE,
    staff_id                 UUID NOT NULL REFERENCES staff(id) ON DELETE CASCADE,
    pay_period_year          INT NOT NULL,
    pay_period_month         INT NOT NULL,                -- 1-12
    version                  INT NOT NULL DEFAULT 1,
    working_days             NUMERIC(5,2) NOT NULL,
    leave_days_paid          NUMERIC(5,2) NOT NULL DEFAULT 0,
    leave_days_unpaid        NUMERIC(5,2) NOT NULL DEFAULT 0,
    gross_paise              BIGINT NOT NULL,
    deductions_paise         BIGINT NOT NULL,
    net_paise                BIGINT NOT NULL,
    breakdown_json           JSONB NOT NULL,             -- full snapshot of the calc inputs
    pdf_url                  TEXT,
    generated_at             TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    generated_by_id          UUID,
    UNIQUE(school_id, staff_id, pay_period_year, pay_period_month, version)
);
CREATE INDEX idx_payslips_staff  ON payslips(staff_id, pay_period_year DESC, pay_period_month DESC);
CREATE INDEX idx_payslips_school ON payslips(school_id, pay_period_year, pay_period_month);


-- ─── from: V17__slice17_admissions.sql ───
-- =====================================================================================
-- Slice 17 — Admissions funnel (ENQUIRY → APPLICATION → TEST → OFFER → ENROLLED)
-- =====================================================================================
-- Single mutable table walks the lifecycle. We capture every transition timestamp instead
-- of a separate state-history table — it keeps lookups O(1) for "where is enquiry X stuck?"
-- analytics, and is sufficient for the linear flow.
--
-- A successful enrolment links to an existing Student row via enrolled_student_id; the
-- service creates the Student inside the same transaction so the link is never dangling.
-- =====================================================================================

CREATE TABLE admissions (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id             UUID NOT NULL REFERENCES schools(id) ON DELETE CASCADE,
    -- Lifecycle status. Values:
    --   ENQUIRY                 - public enquiry just landed
    --   APPLICATION_SUBMITTED   - applicant filled the full application
    --   TEST_SCHEDULED          - entrance test scheduled
    --   TEST_COMPLETED          - test scored, decision pending
    --   OFFERED                 - offer letter issued
    --   ACCEPTED                - parent accepted
    --   DECLINED                - parent declined
    --   ENROLLED                - student row created
    --   WITHDRAWN               - dropped out post-offer (uncommon but real)
    --   REJECTED                - admin rejected (test score / interview)
    status                VARCHAR(30) NOT NULL DEFAULT 'ENQUIRY',
    -- ---------- enquirer details ----------
    parent_name           VARCHAR(200),
    parent_phone          VARCHAR(20) NOT NULL,
    parent_email          VARCHAR(255),
    -- ---------- applicant details ----------
    student_first_name    VARCHAR(100) NOT NULL,
    student_last_name     VARCHAR(100),
    student_date_of_birth DATE,
    student_gender        VARCHAR(10),
    -- ---------- intended placement ----------
    intended_class        VARCHAR(50) NOT NULL,         -- "Class 5"
    intended_section      VARCHAR(20),                  -- optional preference
    intended_academic_year VARCHAR(20),                 -- e.g. "2026-2027"
    -- ---------- attribution ----------
    source                VARCHAR(40),                  -- WALK_IN | WEBSITE | REFERRAL | AD | OTHER
    referrer_name         VARCHAR(200),
    notes                 TEXT,
    -- ---------- entrance test ----------
    test_scheduled_at     TIMESTAMPTZ,
    test_venue            TEXT,
    test_total_marks      INT,
    test_obtained_marks   INT,
    test_remarks          TEXT,
    -- ---------- offer ----------
    offer_letter_url      TEXT,                          -- PDF generated via DocumentService
    offer_issued_at       TIMESTAMPTZ,
    offer_accepted_at     TIMESTAMPTZ,
    offer_declined_at     TIMESTAMPTZ,
    decline_reason        TEXT,
    -- ---------- enrolment outcome ----------
    enrolled_student_id   UUID REFERENCES students(id) ON DELETE SET NULL,
    enrolled_at           TIMESTAMPTZ,
    -- ---------- audit ----------
    assigned_to_id        UUID,                          -- staff handling this funnel item
    created_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by_id         UUID
);
CREATE INDEX idx_admissions_school        ON admissions(school_id);
CREATE INDEX idx_admissions_status        ON admissions(school_id, status);
CREATE INDEX idx_admissions_phone         ON admissions(parent_phone);
CREATE INDEX idx_admissions_created       ON admissions(school_id, created_at DESC);


-- Per-subject test scores. Optional — schools that just record a single total skip this.
CREATE TABLE admission_test_scores (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    admission_id    UUID NOT NULL REFERENCES admissions(id) ON DELETE CASCADE,
    school_id       UUID NOT NULL REFERENCES schools(id) ON DELETE CASCADE,
    subject_name    VARCHAR(100) NOT NULL,
    max_marks       INT NOT NULL,
    obtained_marks  INT NOT NULL,
    remarks         TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE(admission_id, subject_name)
);
CREATE INDEX idx_admission_test_scores_admission ON admission_test_scores(admission_id);


-- ─── from: V18__slice18_aux_modules.sql ───
-- =====================================================================================
-- Slice 18 — Hostel, Cafeteria, Inventory
-- =====================================================================================

-- =====================================================================================
-- 1. HOSTEL
-- =====================================================================================
CREATE TABLE hostels (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id     UUID NOT NULL REFERENCES schools(id) ON DELETE CASCADE,
    name          VARCHAR(120) NOT NULL,
    gender        VARCHAR(10),                 -- BOYS | GIRLS | COED | null
    address       TEXT,
    warden_staff_id UUID,                      -- REFERENCES staff(id) — soft (cross-module)
    total_rooms   INT NOT NULL DEFAULT 0,      -- denormalised, maintained by the service
    capacity      INT NOT NULL DEFAULT 0,
    active        BOOLEAN NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE(school_id, name)
);

CREATE TABLE hostel_rooms (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id       UUID NOT NULL REFERENCES schools(id) ON DELETE CASCADE,
    hostel_id       UUID NOT NULL REFERENCES hostels(id) ON DELETE CASCADE,
    room_number     VARCHAR(20) NOT NULL,
    floor           INT,
    -- SINGLE | DOUBLE | TRIPLE | DORM
    room_type       VARCHAR(15) NOT NULL,
    capacity        INT NOT NULL,
    current_occupancy INT NOT NULL DEFAULT 0,
    notes           TEXT,
    active          BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE(hostel_id, room_number)
);
CREATE INDEX idx_rooms_school ON hostel_rooms(school_id);

CREATE TABLE hostel_allocations (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id         UUID NOT NULL REFERENCES schools(id) ON DELETE CASCADE,
    room_id           UUID NOT NULL REFERENCES hostel_rooms(id) ON DELETE CASCADE,
    student_id        UUID NOT NULL REFERENCES students(id) ON DELETE CASCADE,
    allocated_from    DATE NOT NULL,
    allocated_until   DATE,                       -- NULL = open-ended (vacating fills this)
    status            VARCHAR(10) NOT NULL DEFAULT 'ACTIVE',      -- ACTIVE | VACATED
    vacated_at        TIMESTAMPTZ,
    vacated_reason    TEXT,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by_id     UUID
);
CREATE INDEX idx_alloc_room    ON hostel_allocations(room_id) WHERE status = 'ACTIVE';
CREATE INDEX idx_alloc_student ON hostel_allocations(student_id) WHERE status = 'ACTIVE';
CREATE INDEX idx_alloc_school  ON hostel_allocations(school_id);

CREATE TABLE hostel_visitor_logs (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id       UUID NOT NULL REFERENCES schools(id) ON DELETE CASCADE,
    hostel_id       UUID NOT NULL REFERENCES hostels(id) ON DELETE CASCADE,
    visiting_student_id UUID REFERENCES students(id) ON DELETE SET NULL,
    visitor_name    VARCHAR(200) NOT NULL,
    visitor_phone   VARCHAR(20),
    relation        VARCHAR(50),
    id_proof        VARCHAR(100),
    purpose         TEXT,
    in_time         TIMESTAMPTZ NOT NULL,
    out_time        TIMESTAMPTZ,                       -- NULL = visitor still inside
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by_id   UUID
);
CREATE INDEX idx_visitor_school_in ON hostel_visitor_logs(school_id, in_time DESC);
CREATE INDEX idx_visitor_active    ON hostel_visitor_logs(school_id) WHERE out_time IS NULL;


-- =====================================================================================
-- 2. CAFETERIA
-- =====================================================================================
CREATE TABLE cafeteria_menu_items (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id       UUID NOT NULL REFERENCES schools(id) ON DELETE CASCADE,
    name            VARCHAR(120) NOT NULL,
    -- BREAKFAST | LUNCH | SNACK | DINNER | BEVERAGE | OTHER
    category        VARCHAR(20),
    description     TEXT,
    price_paise     BIGINT NOT NULL,
    image_url       TEXT,
    available       BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_menu_school   ON cafeteria_menu_items(school_id);

CREATE TABLE cafeteria_wallets (
    id                       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id                UUID NOT NULL REFERENCES schools(id) ON DELETE CASCADE,
    student_id               UUID NOT NULL REFERENCES students(id) ON DELETE CASCADE,
    balance_paise            BIGINT NOT NULL DEFAULT 0,
    total_topped_up_paise    BIGINT NOT NULL DEFAULT 0,
    total_spent_paise        BIGINT NOT NULL DEFAULT 0,
    last_topped_up_at        TIMESTAMPTZ,
    created_at               TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at               TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE(school_id, student_id)
);

CREATE TABLE cafeteria_orders (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id         UUID NOT NULL REFERENCES schools(id) ON DELETE CASCADE,
    student_id        UUID NOT NULL REFERENCES students(id) ON DELETE CASCADE,
    wallet_id         UUID REFERENCES cafeteria_wallets(id) ON DELETE SET NULL,
    total_paise       BIGINT NOT NULL,
    -- PLACED | FULFILLED | CANCELLED | REFUNDED
    status            VARCHAR(15) NOT NULL DEFAULT 'PLACED',
    placed_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    fulfilled_at      TIMESTAMPTZ,
    cancelled_at      TIMESTAMPTZ,
    cancel_reason     TEXT,
    notes             TEXT,
    created_by_id     UUID
);
CREATE INDEX idx_orders_student ON cafeteria_orders(student_id, placed_at DESC);
CREATE INDEX idx_orders_school  ON cafeteria_orders(school_id, placed_at DESC);

CREATE TABLE cafeteria_order_items (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id        UUID NOT NULL REFERENCES cafeteria_orders(id) ON DELETE CASCADE,
    menu_item_id    UUID NOT NULL REFERENCES cafeteria_menu_items(id),
    quantity        INT NOT NULL,
    unit_price_paise BIGINT NOT NULL,                  -- snapshotted at order time
    line_total_paise BIGINT NOT NULL
);
CREATE INDEX idx_order_items_order ON cafeteria_order_items(order_id);

CREATE TABLE cafeteria_wallet_transactions (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id       UUID NOT NULL REFERENCES schools(id) ON DELETE CASCADE,
    wallet_id       UUID NOT NULL REFERENCES cafeteria_wallets(id) ON DELETE CASCADE,
    -- TOPUP | DEBIT | REFUND
    txn_type        VARCHAR(10) NOT NULL,
    amount_paise    BIGINT NOT NULL,
    ref_order_id    UUID REFERENCES cafeteria_orders(id) ON DELETE SET NULL,
    balance_after_paise BIGINT NOT NULL,
    notes           TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by_id   UUID
);
CREATE INDEX idx_wallet_txn_wallet ON cafeteria_wallet_transactions(wallet_id, created_at DESC);


-- =====================================================================================
-- 3. INVENTORY
-- =====================================================================================
CREATE TABLE inventory_categories (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id     UUID NOT NULL REFERENCES schools(id) ON DELETE CASCADE,
    name          VARCHAR(120) NOT NULL,
    description   TEXT,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE(school_id, name)
);

CREATE TABLE inventory_items (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id         UUID NOT NULL REFERENCES schools(id) ON DELETE CASCADE,
    category_id       UUID REFERENCES inventory_categories(id) ON DELETE SET NULL,
    asset_tag         VARCHAR(60),                -- school-issued barcode / sticker
    name              VARCHAR(200) NOT NULL,
    description       TEXT,
    serial_number     VARCHAR(120),
    quantity          INT NOT NULL DEFAULT 1,
    unit_cost_paise   BIGINT,
    purchase_date     DATE,
    purchase_invoice_url TEXT,
    -- AVAILABLE | ISSUED | UNDER_MAINTENANCE | LOST | RETIRED
    status            VARCHAR(20) NOT NULL DEFAULT 'AVAILABLE',
    location          TEXT,                        -- "Lab 1", "Library cabinet 3"
    notes             TEXT,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE(school_id, asset_tag)
);
CREATE INDEX idx_inv_items_school   ON inventory_items(school_id);
CREATE INDEX idx_inv_items_status   ON inventory_items(school_id, status);
CREATE INDEX idx_inv_items_category ON inventory_items(category_id);

CREATE TABLE inventory_issuances (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id       UUID NOT NULL REFERENCES schools(id) ON DELETE CASCADE,
    item_id         UUID NOT NULL REFERENCES inventory_items(id) ON DELETE CASCADE,
    -- One of these is set — issued to a staff or a student.
    issued_to_staff_id   UUID,                    -- soft cross-module ref
    issued_to_student_id UUID,                    -- soft cross-module ref
    issued_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    issued_by_id    UUID,
    expected_return_at TIMESTAMPTZ,
    returned_at     TIMESTAMPTZ,
    return_condition VARCHAR(20),                 -- GOOD | DAMAGED | LOST
    notes           TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT issuance_target_xor CHECK (
        (issued_to_staff_id IS NOT NULL)::int + (issued_to_student_id IS NOT NULL)::int = 1
    )
);
CREATE INDEX idx_inv_issuance_item    ON inventory_issuances(item_id);
CREATE INDEX idx_inv_issuance_active  ON inventory_issuances(school_id) WHERE returned_at IS NULL;

CREATE TABLE inventory_maintenance (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id       UUID NOT NULL REFERENCES schools(id) ON DELETE CASCADE,
    item_id         UUID NOT NULL REFERENCES inventory_items(id) ON DELETE CASCADE,
    performed_at    TIMESTAMPTZ NOT NULL,
    cost_paise      BIGINT,
    description     TEXT NOT NULL,
    performed_by    VARCHAR(200),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by_id   UUID
);
CREATE INDEX idx_inv_maint_item ON inventory_maintenance(item_id, performed_at DESC);


-- ─── from: V19__slice19_risk_summary.sql ───
-- =====================================================================================
-- Slice 19 — Plain-English narrative on student_risk_scores
-- =====================================================================================
-- Adds a TEXT column holding a 2-3 sentence LLM-written description of why this student
-- is flagged. Filled by AtRiskDetectionService when an LLM provider is configured;
-- otherwise the service falls back to a templated string so the column is never empty
-- on freshly-scored rows.
-- =====================================================================================

ALTER TABLE student_risk_scores
    ADD COLUMN summary TEXT;


-- ─── from: V20__slice31_fee_structure.sql ───
-- =====================================================================================
-- Slice 31 — Fee structure matrix (per class × fee head × term)
-- =====================================================================================
-- Problem: schools have different fee amounts per class. Admins shouldn't manually
-- create 2,400 invoices per term (800 students × 3 heads). This migration adds the
-- matrix data model so the bulk invoice-generator (Slice 31c) can populate
-- fee_invoices for every enrolled student in one click.
--
-- Design:
--   1. fee_structure_versions  — one DRAFT/ACTIVE/ARCHIVED snapshot per academic year
--   2. fee_structure_terms     — optional N-term split (annual = no rows)
--   3. fee_structure_rows      — the matrix cells; one row per (class × head × term)
--
-- Idempotency: re-running invoice generation for the same (version, term, student)
-- triggers a UNIQUE constraint at the fee_invoices level via a partial index added
-- below — preventing the most common "I clicked it twice" double-billing bug.
-- =====================================================================================

CREATE TABLE fee_structure_versions (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id           UUID NOT NULL REFERENCES schools(id) ON DELETE CASCADE,
    academic_year_id    UUID NOT NULL REFERENCES academic_years(id) ON DELETE CASCADE,
    name                VARCHAR(120) NOT NULL,
    status              VARCHAR(15) NOT NULL DEFAULT 'DRAFT',   -- DRAFT | ACTIVE | ARCHIVED
    notes               TEXT,
    activated_at        TIMESTAMPTZ,
    archived_at         TIMESTAMPTZ,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by_id       UUID,
    -- A school can have many DRAFT versions but only one ACTIVE per academic year.
    -- Enforced at the application layer (FeeStructureService.activate) since Postgres
    -- partial unique indexes on enum values are noisy and this is a low-write surface.
    UNIQUE(school_id, academic_year_id, name)
);
CREATE INDEX idx_fee_struct_versions_school ON fee_structure_versions(school_id);
CREATE INDEX idx_fee_struct_versions_active ON fee_structure_versions(school_id, academic_year_id)
    WHERE status = 'ACTIVE';

-- Optional term split. Schools that bill annually skip this entirely; row count = 0
-- → invoice generator treats the structure as single-term-due-on-academic-year-start.
CREATE TABLE fee_structure_terms (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    version_id          UUID NOT NULL REFERENCES fee_structure_versions(id) ON DELETE CASCADE,
    term_number         INT NOT NULL,                          -- 1, 2, 3 …
    name                VARCHAR(80) NOT NULL,                  -- "Term 1 — Apr-Jul"
    start_date          DATE NOT NULL,
    end_date            DATE NOT NULL,
    due_date            DATE NOT NULL,                         -- when this term's invoices are due
    UNIQUE(version_id, term_number),
    CONSTRAINT fee_struct_term_dates CHECK (end_date >= start_date AND due_date >= start_date)
);
CREATE INDEX idx_fee_struct_terms_version ON fee_structure_terms(version_id);

-- The matrix cells. (class × head × term) — term is nullable for annual structures.
-- Amount = 0 is legal (some heads only apply to some classes; you could either
-- omit the row OR insert a zero row — both mean "nothing to bill"; we recommend
-- omitting for cleanliness but tolerate zeros).
CREATE TABLE fee_structure_rows (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    version_id          UUID NOT NULL REFERENCES fee_structure_versions(id) ON DELETE CASCADE,
    class_id            UUID NOT NULL REFERENCES school_classes(id) ON DELETE CASCADE,
    fee_head_id         UUID NOT NULL REFERENCES fee_heads(id) ON DELETE CASCADE,
    term_number         INT,                                   -- NULL = annual
    amount_paise        BIGINT NOT NULL,
    -- True for fees the student opts into (transport, library) — generator skips these
    -- unless a per-student opt-in record exists (deferred to a later slice; default off).
    is_optional         BOOLEAN NOT NULL DEFAULT FALSE,
    UNIQUE(version_id, class_id, fee_head_id, term_number),
    CONSTRAINT fee_struct_row_amount_non_negative CHECK (amount_paise >= 0)
);
CREATE INDEX idx_fee_struct_rows_version ON fee_structure_rows(version_id);
CREATE INDEX idx_fee_struct_rows_class   ON fee_structure_rows(class_id);


-- =====================================================================================
-- Idempotency guard on fee_invoices: prevent the generator from issuing the same
-- (student, head, term, source-version) invoice twice. We add a NULLABLE column
-- pointing back to the structure row that birthed each invoice; the partial unique
-- index only constrains generator-created invoices, leaving manually-created
-- invoices (opening balances, ad-hoc) unaffected.
-- =====================================================================================
ALTER TABLE fee_invoices
    ADD COLUMN structure_version_id UUID REFERENCES fee_structure_versions(id) ON DELETE SET NULL,
    ADD COLUMN structure_term_number INT;

CREATE UNIQUE INDEX uq_fee_invoices_per_structure
    ON fee_invoices(school_id, student_id, fee_head_id, structure_version_id, structure_term_number)
    WHERE structure_version_id IS NOT NULL;


-- =====================================================================================
-- Feature key + plan mapping. Reuses existing seeding pattern from V14.
-- =====================================================================================
INSERT INTO features (feature_key, name, description, default_enabled, category) VALUES
    ('FEE_STRUCTURE',
     'Fee structure matrix',
     'Per-class fee structure with one-click bulk invoice generation',
     FALSE, 'Finance')
ON CONFLICT (feature_key) DO NOTHING;

WITH p AS (SELECT id, code FROM plans)
INSERT INTO plan_features (plan_id, feature_key)
SELECT p.id, f.feature_key
FROM p
CROSS JOIN LATERAL (VALUES
    ('STARTER',    'FEE_STRUCTURE'),
    ('GROWTH',     'FEE_STRUCTURE'),
    ('ENTERPRISE', 'FEE_STRUCTURE')
) AS f(plan_code, feature_key)
WHERE p.code = f.plan_code
ON CONFLICT DO NOTHING;


-- ─── from: V21__slice33_notifications_and_gaps.sql ───
-- =====================================================================================
-- Slice 33-34 — parent notification categories + daily-ops gap-filler feature flags.
--
-- Strategy: every new feature is registered in the features() catalog with default_enabled=
-- TRUE for "always-on" categories (absence, fee receipts) and FALSE for opt-in extras
-- (homework, birthday wishes). Tenant admins flip per-flag via existing FeatureOverride.
--
-- Plan mapping: parent notifications are bundled with STARTER+ (it's table stakes); the
-- daily-ops gap-fillers go to GROWTH/ENTERPRISE so smaller schools get a clean baseline.
-- =====================================================================================

INSERT INTO features (feature_key, name, description, default_enabled, category) VALUES
    -- Parent notification umbrella + per-category
    ('PARENT_NOTIFICATIONS',       'Parent notifications',       'Master switch for WA + email alerts to parents', TRUE,  'Communication'),
    ('PARENT_NOTIFY_ABSENCE',      'Notify on absence',          'Send WA/email when a student is marked absent',  TRUE,  'Communication'),
    ('PARENT_NOTIFY_LATE_ARRIVAL', 'Notify on late arrival',     'Send WA/email when a student is marked late',    TRUE,  'Communication'),
    ('PARENT_NOTIFY_FEE_RECEIPT',  'Notify on fee receipt',      'Send receipt PDF after payment',                 TRUE,  'Communication'),
    ('PARENT_NOTIFY_FEE_DUE',      'Notify on fee due soon',     'Daily cron — invoices due in 3 / 1 / 0 days',    TRUE,  'Communication'),
    ('PARENT_NOTIFY_FEE_OVERDUE',  'Notify on fee overdue',      'Daily cron — invoices past due date',            TRUE,  'Communication'),
    ('PARENT_NOTIFY_REPORT_CARD',  'Notify on report card',      'Send report card PDF when published',            TRUE,  'Communication'),
    ('PARENT_NOTIFY_MARKS',        'Notify on marks finalized',  'Per-subject marks push',                          FALSE, 'Communication'),
    ('PARENT_NOTIFY_HOMEWORK',     'Notify on homework assigned','Daily summary of homework for the child',         FALSE, 'Communication'),
    ('PARENT_NOTIFY_LIBRARY_OVERDUE','Notify library overdue',    'Daily — books past return date',                  FALSE, 'Communication'),
    ('PARENT_NOTIFY_EXAM_SCHEDULE','Notify exam schedule',       'Hall ticket + datesheet on publish',              TRUE,  'Communication'),
    ('PARENT_NOTIFY_CIRCULAR',     'Notify on circular',         'Auto-send every published circular',              TRUE,  'Communication'),
    ('PARENT_NOTIFY_BIRTHDAY',     'Birthday greeting',          'Send birthday wishes to student via parent',      FALSE, 'Communication'),

    -- Daily-ops gap fillers
    ('VISITOR_MANAGEMENT',     'Visitor log',           'Front-desk in/out + host staff + purpose',         FALSE, 'Operations'),
    ('CASH_RECONCILIATION',    'Day-end cash recon',    'Today''s collection by mode, drawer open/close',   TRUE,  'Finance'),
    ('EXPENSE_TRACKING',       'Expense tracking',      'Per-tenant expense entry with categories',         FALSE, 'Finance'),
    ('INCIDENT_LOG',           'Incident log',          'Behaviour / discipline / merit-demerit',           FALSE, 'Operations'),
    ('INFIRMARY_LOG',          'Infirmary visits',      'Nurse log + medication + parent notify',           FALSE, 'Operations'),
    ('STUDENT_DOCUMENT_VAULT', 'Student doc vault',     'Aadhaar, birth cert, prev marksheet attachments',  FALSE, 'Operations'),
    ('UNIFIED_INBOX',          'Unified inbox',         'Sent WA + SMS + email timeline per student',       FALSE, 'Communication'),
    ('ALERTS_FEED',            'Alerts feed',           'Persistent alerts inbox page',                     TRUE,  'Operations'),
    ('LOW_STOCK_ALERTS',       'Low-stock alerts',      'Inventory threshold notifications',                FALSE, 'Operations'),
    ('PTM_SCHEDULING',         'Parent-teacher meetings','Slot-based PTM booking',                          FALSE, 'Communication')
ON CONFLICT (feature_key) DO NOTHING;

-- Map parent-notifications to every plan above FREE so demos work out of the box.
WITH p AS (SELECT id, code FROM plans)
INSERT INTO plan_features (plan_id, feature_key)
SELECT p.id, f.feature_key
FROM p
CROSS JOIN LATERAL (VALUES
    ('STARTER',    'PARENT_NOTIFICATIONS'),
    ('STARTER',    'PARENT_NOTIFY_ABSENCE'),
    ('STARTER',    'PARENT_NOTIFY_LATE_ARRIVAL'),
    ('STARTER',    'PARENT_NOTIFY_FEE_RECEIPT'),
    ('STARTER',    'PARENT_NOTIFY_FEE_DUE'),
    ('STARTER',    'PARENT_NOTIFY_FEE_OVERDUE'),
    ('STARTER',    'PARENT_NOTIFY_REPORT_CARD'),
    ('STARTER',    'PARENT_NOTIFY_EXAM_SCHEDULE'),
    ('STARTER',    'PARENT_NOTIFY_CIRCULAR'),
    ('GROWTH',     'PARENT_NOTIFICATIONS'),
    ('GROWTH',     'PARENT_NOTIFY_ABSENCE'),
    ('GROWTH',     'PARENT_NOTIFY_LATE_ARRIVAL'),
    ('GROWTH',     'PARENT_NOTIFY_FEE_RECEIPT'),
    ('GROWTH',     'PARENT_NOTIFY_FEE_DUE'),
    ('GROWTH',     'PARENT_NOTIFY_FEE_OVERDUE'),
    ('GROWTH',     'PARENT_NOTIFY_REPORT_CARD'),
    ('GROWTH',     'PARENT_NOTIFY_MARKS'),
    ('GROWTH',     'PARENT_NOTIFY_HOMEWORK'),
    ('GROWTH',     'PARENT_NOTIFY_LIBRARY_OVERDUE'),
    ('GROWTH',     'PARENT_NOTIFY_EXAM_SCHEDULE'),
    ('GROWTH',     'PARENT_NOTIFY_CIRCULAR'),
    ('GROWTH',     'PARENT_NOTIFY_BIRTHDAY'),
    ('GROWTH',     'CASH_RECONCILIATION'),
    ('GROWTH',     'ALERTS_FEED'),
    ('GROWTH',     'STUDENT_DOCUMENT_VAULT'),
    ('GROWTH',     'UNIFIED_INBOX'),
    ('ENTERPRISE', 'PARENT_NOTIFICATIONS'),
    ('ENTERPRISE', 'PARENT_NOTIFY_ABSENCE'),
    ('ENTERPRISE', 'PARENT_NOTIFY_LATE_ARRIVAL'),
    ('ENTERPRISE', 'PARENT_NOTIFY_FEE_RECEIPT'),
    ('ENTERPRISE', 'PARENT_NOTIFY_FEE_DUE'),
    ('ENTERPRISE', 'PARENT_NOTIFY_FEE_OVERDUE'),
    ('ENTERPRISE', 'PARENT_NOTIFY_REPORT_CARD'),
    ('ENTERPRISE', 'PARENT_NOTIFY_MARKS'),
    ('ENTERPRISE', 'PARENT_NOTIFY_HOMEWORK'),
    ('ENTERPRISE', 'PARENT_NOTIFY_LIBRARY_OVERDUE'),
    ('ENTERPRISE', 'PARENT_NOTIFY_EXAM_SCHEDULE'),
    ('ENTERPRISE', 'PARENT_NOTIFY_CIRCULAR'),
    ('ENTERPRISE', 'PARENT_NOTIFY_BIRTHDAY'),
    ('ENTERPRISE', 'VISITOR_MANAGEMENT'),
    ('ENTERPRISE', 'CASH_RECONCILIATION'),
    ('ENTERPRISE', 'EXPENSE_TRACKING'),
    ('ENTERPRISE', 'INCIDENT_LOG'),
    ('ENTERPRISE', 'INFIRMARY_LOG'),
    ('ENTERPRISE', 'STUDENT_DOCUMENT_VAULT'),
    ('ENTERPRISE', 'UNIFIED_INBOX'),
    ('ENTERPRISE', 'ALERTS_FEED'),
    ('ENTERPRISE', 'LOW_STOCK_ALERTS'),
    ('ENTERPRISE', 'PTM_SCHEDULING')
) AS f(plan_code, feature_key)
WHERE p.code = f.plan_code
ON CONFLICT DO NOTHING;


-- ─── from: V22__slice34_daily_ops_modules.sql ───
-- =====================================================================================
-- Slice 34 — daily-ops gap fillers. One migration creates the storage for every module
-- registered in Slice 33's feature-flag catalog. Each table is school_id-scoped (multi-
-- tenant) and FK-cascaded so deleting a school cleans up its rows.
-- =====================================================================================

-- ---------- Visitor management ----------
CREATE TABLE visitors (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id       UUID NOT NULL REFERENCES schools(id) ON DELETE CASCADE,
    name            VARCHAR(120) NOT NULL,
    phone           VARCHAR(30),
    purpose         VARCHAR(255),
    host_staff_id   UUID REFERENCES staff(id) ON DELETE SET NULL,
    host_student_id UUID REFERENCES students(id) ON DELETE SET NULL,
    badge_number    VARCHAR(40),
    photo_url       TEXT,
    in_at           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    out_at          TIMESTAMPTZ,
    notes           TEXT,
    created_by_id   UUID
);
CREATE INDEX idx_visitors_school_in  ON visitors(school_id, in_at DESC);
CREATE INDEX idx_visitors_open       ON visitors(school_id) WHERE out_at IS NULL;

-- ---------- Day-end cash reconciliation ----------
-- The "drawer" is a per-day-per-staff accumulator; payments already record their mode, so we
-- aggregate at close-time and store the expected-vs-counted variance.
CREATE TABLE cash_reconciliations (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id       UUID NOT NULL REFERENCES schools(id) ON DELETE CASCADE,
    closed_by_id    UUID NOT NULL,
    closed_on_date  DATE NOT NULL,
    expected_cash_paise   BIGINT NOT NULL,
    expected_upi_paise    BIGINT NOT NULL,
    expected_cheque_paise BIGINT NOT NULL,
    expected_other_paise  BIGINT NOT NULL,
    counted_cash_paise    BIGINT NOT NULL,
    counted_upi_paise     BIGINT NOT NULL,
    counted_cheque_paise  BIGINT NOT NULL,
    counted_other_paise   BIGINT NOT NULL,
    variance_paise        BIGINT NOT NULL,
    notes           TEXT,
    closed_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (school_id, closed_on_date, closed_by_id)
);
CREATE INDEX idx_cash_recon_school_date ON cash_reconciliations(school_id, closed_on_date DESC);

-- ---------- Expense tracking ----------
CREATE TABLE expense_categories (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id   UUID NOT NULL REFERENCES schools(id) ON DELETE CASCADE,
    name        VARCHAR(80) NOT NULL,
    active      BOOLEAN NOT NULL DEFAULT TRUE,
    UNIQUE (school_id, name)
);
CREATE TABLE expenses (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id       UUID NOT NULL REFERENCES schools(id) ON DELETE CASCADE,
    category_id     UUID REFERENCES expense_categories(id) ON DELETE SET NULL,
    amount_paise    BIGINT NOT NULL CHECK (amount_paise >= 0),
    spent_on        DATE NOT NULL,
    vendor          VARCHAR(120),
    description     TEXT,
    receipt_url     TEXT,
    payment_mode    VARCHAR(20),                   -- CASH / UPI / CHEQUE / CARD / BANK
    recorded_by_id  UUID,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_expenses_school_date ON expenses(school_id, spent_on DESC);

-- ---------- Incident / behaviour log ----------
CREATE TABLE incidents (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id       UUID NOT NULL REFERENCES schools(id) ON DELETE CASCADE,
    student_id      UUID NOT NULL REFERENCES students(id) ON DELETE CASCADE,
    severity        VARCHAR(15) NOT NULL,           -- MINOR / MAJOR / SEVERE
    incident_type   VARCHAR(40) NOT NULL,           -- MERIT / DEMERIT / DISCIPLINE / ACADEMIC
    points          INTEGER NOT NULL DEFAULT 0,     -- + for merit, - for demerit
    occurred_on     DATE NOT NULL,
    description     TEXT NOT NULL,
    action_taken    TEXT,
    reported_by_id  UUID NOT NULL,
    parent_notified BOOLEAN NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_incidents_student ON incidents(student_id, occurred_on DESC);
CREATE INDEX idx_incidents_school  ON incidents(school_id, occurred_on DESC);

-- ---------- Infirmary visit log ----------
CREATE TABLE infirmary_visits (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id       UUID NOT NULL REFERENCES schools(id) ON DELETE CASCADE,
    student_id      UUID NOT NULL REFERENCES students(id) ON DELETE CASCADE,
    visited_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    complaint       TEXT NOT NULL,
    treatment       TEXT,
    medicine_given  TEXT,
    temperature_c   NUMERIC(4,1),
    pulse           INTEGER,
    sent_home       BOOLEAN NOT NULL DEFAULT FALSE,
    parent_notified BOOLEAN NOT NULL DEFAULT FALSE,
    recorded_by_id  UUID NOT NULL,
    notes           TEXT
);
CREATE INDEX idx_infirmary_student ON infirmary_visits(student_id, visited_at DESC);
CREATE INDEX idx_infirmary_school  ON infirmary_visits(school_id, visited_at DESC);

-- Per-student static medical record — surfaced on the marking screen + infirmary screen.
CREATE TABLE student_medical_records (
    student_id          UUID PRIMARY KEY REFERENCES students(id) ON DELETE CASCADE,
    school_id           UUID NOT NULL REFERENCES schools(id) ON DELETE CASCADE,
    blood_group         VARCHAR(5),
    allergies           TEXT,
    chronic_conditions  TEXT,
    medications         TEXT,
    emergency_contact   VARCHAR(120),
    emergency_phone     VARCHAR(30),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- ---------- Student document vault ----------
-- A V5 students-only table exists; this one is a generic per-student multi-doc bucket.
CREATE TABLE student_vault_documents (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id       UUID NOT NULL REFERENCES schools(id) ON DELETE CASCADE,
    student_id      UUID NOT NULL REFERENCES students(id) ON DELETE CASCADE,
    doc_type        VARCHAR(50) NOT NULL,           -- AADHAAR, BIRTH_CERT, PREV_MARKSHEET, …
    file_name       VARCHAR(255) NOT NULL,
    file_url        TEXT NOT NULL,
    mime_type       VARCHAR(120),
    size_bytes      BIGINT,
    uploaded_by_id  UUID NOT NULL,
    uploaded_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    notes           TEXT
);
CREATE INDEX idx_vault_student ON student_vault_documents(student_id, uploaded_at DESC);

-- ---------- Inventory low-stock thresholds + alerts ----------
-- Tiny extension to existing inventory tables. We only add a threshold column + a denormalised
-- "alerted_at" timestamp so the cron doesn't re-fire every tick.
ALTER TABLE inventory_items
    ADD COLUMN IF NOT EXISTS low_stock_threshold INTEGER,
    ADD COLUMN IF NOT EXISTS low_stock_alerted_at TIMESTAMPTZ;

-- ---------- Parent-teacher meeting (PTM) scheduling ----------
CREATE TABLE ptm_slots (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id       UUID NOT NULL REFERENCES schools(id) ON DELETE CASCADE,
    teacher_id      UUID NOT NULL REFERENCES staff(id) ON DELETE CASCADE,
    slot_date       DATE NOT NULL,
    start_time      TIME NOT NULL,
    end_time        TIME NOT NULL,
    capacity        INTEGER NOT NULL DEFAULT 1,
    booked_count    INTEGER NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (teacher_id, slot_date, start_time)
);
CREATE INDEX idx_ptm_slots_school ON ptm_slots(school_id, slot_date);

CREATE TABLE ptm_bookings (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    slot_id         UUID NOT NULL REFERENCES ptm_slots(id) ON DELETE CASCADE,
    school_id       UUID NOT NULL REFERENCES schools(id) ON DELETE CASCADE,
    student_id      UUID NOT NULL REFERENCES students(id) ON DELETE CASCADE,
    parent_id       UUID REFERENCES parents(id) ON DELETE SET NULL,
    status          VARCHAR(15) NOT NULL DEFAULT 'CONFIRMED',  -- CONFIRMED / CANCELLED / COMPLETED
    notes           TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (slot_id, student_id)
);
CREATE INDEX idx_ptm_bookings_slot ON ptm_bookings(slot_id);
CREATE INDEX idx_ptm_bookings_student ON ptm_bookings(student_id, created_at DESC);

-- ---------- Unified messages timeline ----------
-- A denormalised reader-friendly view of every outbound message (WA + email + SMS).
-- notification_log already records sends; this table is a per-student inbox cache that the
-- /inbox page reads in O(log n). The CircularService and ParentNotificationService write here.
CREATE TABLE parent_messages (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id       UUID NOT NULL REFERENCES schools(id) ON DELETE CASCADE,
    student_id      UUID REFERENCES students(id) ON DELETE CASCADE,
    parent_id       UUID REFERENCES parents(id) ON DELETE SET NULL,
    channel         VARCHAR(15) NOT NULL,           -- WHATSAPP / EMAIL / SMS
    category        VARCHAR(40) NOT NULL,           -- mirrors FeatureKey.PARENT_NOTIFY_*
    subject         VARCHAR(255),
    body            TEXT NOT NULL,
    media_url       TEXT,
    status          VARCHAR(15) NOT NULL DEFAULT 'SENT',  -- SENT / DELIVERED / READ / FAILED
    sent_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    delivered_at    TIMESTAMPTZ,
    read_at         TIMESTAMPTZ
);
CREATE INDEX idx_parent_msg_student   ON parent_messages(student_id, sent_at DESC);
CREATE INDEX idx_parent_msg_school    ON parent_messages(school_id, sent_at DESC);

-- ---------- Persistent alerts feed ----------
-- The analytics module's `alerts` table already stores in-the-now alerts; we add a generic
-- "user_alerts" view-state so each staff member can mark them read.
CREATE TABLE alert_dismissals (
    alert_id        UUID NOT NULL,                  -- analytics.alerts.id; loose FK to avoid module coupling
    staff_id        UUID NOT NULL REFERENCES staff(id) ON DELETE CASCADE,
    dismissed_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (alert_id, staff_id)
);


-- ─── from: V23__slice35_password_login.sql ───
-- =====================================================================================
-- Slice 35 — Optional password login alongside OTP.
--
-- Why: schools that don't want OTP-every-time asked for "remember-me" style passwords.
-- The OTP flow stays (still required for first signup verification + password reset);
-- once a user sets a password they can choose Password OR OTP at login.
--
-- BCrypt hashes are 60 chars (work-factor 10 by default); we leave room for stronger
-- algorithms (Argon2, work-factor bumps) by reserving 100 chars.
-- =====================================================================================

ALTER TABLE staff
    ADD COLUMN IF NOT EXISTS password_hash       VARCHAR(100),
    -- Track when we last verified the identifier so cooldown / abuse-detection has data.
    ADD COLUMN IF NOT EXISTS identifier_verified BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS identifier_verified_at TIMESTAMPTZ,
    -- For password-reset throttling.
    ADD COLUMN IF NOT EXISTS password_set_at     TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS failed_login_count  INT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS locked_until        TIMESTAMPTZ;

-- Existing pre-Slice-35 staff are already "verified" because they signed up via the
-- old OTP-only flow which implicitly verified them at first login. Mark them so they
-- aren't forced through verification again.
UPDATE staff SET identifier_verified = TRUE, identifier_verified_at = COALESCE(created_at, NOW())
WHERE identifier_verified = FALSE;


-- ─── from: V24__teacher_invite_onboarding.sql ───
-- =====================================================================================
-- V24 — Teacher invite / onboarding via email
--
-- Adds must_reset_password so admins can pre-set a temp password for a teacher
-- and the teacher is forced to change it on first login.
-- =====================================================================================

ALTER TABLE staff
    ADD COLUMN IF NOT EXISTS must_reset_password BOOLEAN NOT NULL DEFAULT FALSE;


-- ─── from: V25__add_created_by_id_to_missing_tables.sql ───
-- =====================================================================================
-- V25 — Back-fill created_by_id audit column on tables that predated BaseEntity audit
-- =====================================================================================
-- The following tables were created before BaseEntity started carrying created_by_id,
-- so Hibernate schema validation fails on startup.  Adding the column as nullable
-- preserves existing rows without data loss.
-- =====================================================================================

ALTER TABLE feature_overrides  ADD COLUMN IF NOT EXISTS created_by_id UUID;
ALTER TABLE timetable_periods  ADD COLUMN IF NOT EXISTS created_by_id UUID;
ALTER TABLE timetable_entries  ADD COLUMN IF NOT EXISTS created_by_id UUID;


-- ─── from: V26__add_updated_at_to_timetable_periods.sql ───
-- =====================================================================================
-- V26 — Back-fill updated_at audit column on timetable_periods
-- =====================================================================================
-- timetable_periods was created in V9 without updated_at, while BaseEntity requires it.
-- =====================================================================================

ALTER TABLE timetable_periods ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW();


-- ─── from: V27__message_templates_and_notification_resend.sql ───
-- V27: configurable per-tenant message templates + notification_log resend support
-- Allows schools to override the default WhatsApp/SMS message bodies for common events.

CREATE TABLE message_templates (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id   UUID        NOT NULL REFERENCES schools(id) ON DELETE CASCADE,
    template_key VARCHAR(60) NOT NULL,  -- matches MessageType enum name
    body_template TEXT       NOT NULL,
    updated_by  UUID         REFERENCES staff(id),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_message_templates_school_key UNIQUE (school_id, template_key)
);

CREATE INDEX idx_message_templates_school ON message_templates(school_id);

-- Extend notification_log with resend support
ALTER TABLE notification_log
    ADD COLUMN IF NOT EXISTS retry_count   INT         NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS retried_at    TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS parent_log_id UUID        REFERENCES notification_log(id);

-- Add index for paginated log queries (list by school ordered by recency)
CREATE INDEX IF NOT EXISTS idx_notification_log_school_created
    ON notification_log(school_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_notification_log_school_status
    ON notification_log(school_id, status, created_at DESC);


-- ─── from: V28__exam_result_module.sql ───
-- ============================================================
-- V28: Exam & Result Management Module
-- Adds component-wise marking structure, marks entry, computed
-- results, and result lifecycle (DRAFT → READY → PUBLISHED).
-- ============================================================

-- ============================================================
-- Extend existing exams table
-- ============================================================
ALTER TABLE exams
    ADD COLUMN IF NOT EXISTS class_id       UUID REFERENCES school_classes(id),
    ADD COLUMN IF NOT EXISTS section_id     UUID REFERENCES sections(id),
    ADD COLUMN IF NOT EXISTS result_status  VARCHAR(15) NOT NULL DEFAULT 'DRAFT';

-- ============================================================
-- Extend existing exam_marks table (teacher remarks per subject)
-- ============================================================
ALTER TABLE exam_marks
    ADD COLUMN IF NOT EXISTS remarks TEXT;

-- ============================================================
-- Exam subject configs — the marking scheme blueprint.
-- Admin configures which components a subject has for a given exam.
-- e.g. Science → Theory(70) + Practical(30); Math → Theory(100)
-- ============================================================
CREATE TABLE IF NOT EXISTS exam_subject_configs (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id       UUID NOT NULL REFERENCES schools(id),
    exam_id         UUID NOT NULL REFERENCES exams(id) ON DELETE CASCADE,
    subject_id      UUID NOT NULL REFERENCES subjects(id),
    component_name  VARCHAR(50) NOT NULL,
    max_marks       NUMERIC(6,2) NOT NULL,
    passing_marks   NUMERIC(6,2),
    sort_order      INTEGER NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE(exam_id, subject_id, component_name)
);

CREATE INDEX IF NOT EXISTS idx_esc_exam_subject
    ON exam_subject_configs(exam_id, subject_id);
CREATE INDEX IF NOT EXISTS idx_esc_school_exam
    ON exam_subject_configs(school_id, exam_id);

-- ============================================================
-- Exam component marks — teacher enters marks per component.
-- Upsert-safe via unique(config_id, student_id).
-- After save the service rolls totals up into exam_marks for
-- backward-compatible report-card generation.
-- ============================================================
CREATE TABLE IF NOT EXISTS exam_component_marks (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id   UUID NOT NULL REFERENCES schools(id),
    config_id   UUID NOT NULL REFERENCES exam_subject_configs(id) ON DELETE CASCADE,
    student_id  UUID NOT NULL REFERENCES students(id),
    section_id  UUID NOT NULL REFERENCES sections(id),
    obtained    NUMERIC(6,2),
    is_absent   BOOLEAN NOT NULL DEFAULT FALSE,
    is_draft    BOOLEAN NOT NULL DEFAULT TRUE,
    remarks     TEXT,
    entered_by  UUID REFERENCES staff(id),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE(config_id, student_id)
);

CREATE INDEX IF NOT EXISTS idx_ecm_config_section
    ON exam_component_marks(config_id, section_id);
CREATE INDEX IF NOT EXISTS idx_ecm_student_section
    ON exam_component_marks(student_id, section_id);
CREATE INDEX IF NOT EXISTS idx_ecm_school_section
    ON exam_component_marks(school_id, section_id);

-- ============================================================
-- Exam results — one computed aggregate per student per exam.
-- Auto-computed by ResultService; re-runnable (upsert).
-- Dense rank within section, percentage to 2 dp.
-- ============================================================
CREATE TABLE IF NOT EXISTS exam_results (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id       UUID NOT NULL REFERENCES schools(id),
    exam_id         UUID NOT NULL REFERENCES exams(id) ON DELETE CASCADE,
    student_id      UUID NOT NULL REFERENCES students(id),
    section_id      UUID NOT NULL REFERENCES sections(id),
    total_max       NUMERIC(7,2) NOT NULL,
    total_obtained  NUMERIC(7,2) NOT NULL,
    percentage      NUMERIC(5,2) NOT NULL,
    grade           VARCHAR(5),
    rank_in_section INTEGER,
    is_pass         BOOLEAN NOT NULL DEFAULT FALSE,
    status          VARCHAR(15) NOT NULL DEFAULT 'DRAFT',
    computed_at     TIMESTAMPTZ,
    published_at    TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE(exam_id, student_id)
);

CREATE INDEX IF NOT EXISTS idx_er_exam_section
    ON exam_results(exam_id, section_id);
CREATE INDEX IF NOT EXISTS idx_er_exam_status
    ON exam_results(exam_id, status);
CREATE INDEX IF NOT EXISTS idx_er_school_exam
    ON exam_results(school_id, exam_id);


-- ─── from: V29__exam_section_lock.sql ───
-- ============================================================
-- V29: Exam section submission lock
-- When the class teacher does "Submit All (Final)" for their section,
-- a row is inserted here. After that, subject teachers and class
-- teachers can no longer edit marks. Only Principal/Admin can override.
-- ============================================================

CREATE TABLE IF NOT EXISTS exam_section_submissions (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id       UUID NOT NULL REFERENCES schools(id),
    exam_id         UUID NOT NULL REFERENCES exams(id) ON DELETE CASCADE,
    section_id      UUID NOT NULL REFERENCES sections(id),
    submitted_by    UUID NOT NULL REFERENCES staff(id),
    submitted_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE(exam_id, section_id)
);

CREATE INDEX IF NOT EXISTS idx_ess_exam_section
    ON exam_section_submissions(exam_id, section_id);


-- ─── from: V30__attendance_section_lock.sql ───
-- V30: Attendance section lock
-- When a CLASS_TEACHER submits attendance for their section, we record it here.
-- Any re-submission by a non-principal/admin is blocked.
-- Idempotent insert via ON CONFLICT DO NOTHING.

CREATE TABLE IF NOT EXISTS attendance_section_locks (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id     UUID NOT NULL REFERENCES schools(id),
    section_id    UUID NOT NULL REFERENCES sections(id),
    date          DATE NOT NULL,
    submitted_by  UUID NOT NULL REFERENCES staff(id),
    submitted_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE(section_id, date)
);

CREATE INDEX IF NOT EXISTS idx_asl_section_date ON attendance_section_locks(section_id, date);


-- ─── from: V31__admit_cards.sql ───
-- V31: Admit card (hall ticket) generation with fee-clearance gate
-- status: BLOCKED = unpaid fees, PENDING = eligible but not yet generated,
--         GENERATED = PDF stored, DOWNLOADED = downloaded at least once

CREATE TABLE admit_cards (
    id                          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id                   UUID        NOT NULL REFERENCES schools(id),
    exam_id                     UUID        NOT NULL REFERENCES exams(id) ON DELETE CASCADE,
    student_id                  UUID        NOT NULL REFERENCES students(id),
    status                      TEXT        NOT NULL DEFAULT 'PENDING',
    -- null while BLOCKED; populated once PDF is generated
    pdf_url                     TEXT,
    admit_card_no               TEXT,
    seat_number                 TEXT,
    -- snapshot of outstanding paise at generation time (0 = cleared)
    outstanding_paise_snapshot  BIGINT      NOT NULL DEFAULT 0,
    fee_cleared                 BOOLEAN     NOT NULL DEFAULT FALSE,
    generated_at                TIMESTAMPTZ,
    -- last time a notification was sent about this card being blocked
    last_notified_at            TIMESTAMPTZ,
    created_at                  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at                  TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE(exam_id, student_id)
);

CREATE INDEX idx_admit_cards_exam    ON admit_cards(exam_id);
CREATE INDEX idx_admit_cards_student ON admit_cards(student_id);
CREATE INDEX idx_admit_cards_school  ON admit_cards(school_id);
CREATE INDEX idx_admit_cards_status  ON admit_cards(school_id, status);

-- Per-exam settings for auto-generation and fee-clearance gate
CREATE TABLE admit_card_settings (
    id                      UUID    PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id               UUID    NOT NULL REFERENCES schools(id),
    exam_id                 UUID    NOT NULL REFERENCES exams(id) ON DELETE CASCADE,
    days_before             INT     NOT NULL DEFAULT 5,
    fee_clearance_required  BOOLEAN NOT NULL DEFAULT TRUE,
    notify_blocked          BOOLEAN NOT NULL DEFAULT TRUE,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE(school_id, exam_id)
);


-- ─── from: V32__student_leave_and_substitutions.sql ───
-- V10: student leave applications + timetable substitutions
-- Two tables created here:
--   1. student_leave_applications — tracks student leave requests and decisions.
--   2. timetable_substitutions    — tracks substitute teacher assignments for a period/date.

-- ============================================================
-- 1. Student Leave Applications
-- ============================================================
CREATE TABLE IF NOT EXISTS student_leave_applications (
    id                   UUID        NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    school_id            UUID        NOT NULL,
    student_id           UUID        NOT NULL REFERENCES students(id),
    section_id           UUID        NOT NULL REFERENCES sections(id),
    start_date           DATE        NOT NULL,
    end_date             DATE        NOT NULL,
    days                 INTEGER     NOT NULL CHECK (days > 0),
    reason               TEXT,
    status               VARCHAR(20) NOT NULL DEFAULT 'SUBMITTED'
                             CHECK (status IN ('SUBMITTED','APPROVED','REJECTED','CANCELLED')),
    applied_by_staff_id  UUID        REFERENCES staff(id),
    decided_by_staff_id  UUID        REFERENCES staff(id),
    decision_note        TEXT,
    decided_at           TIMESTAMPTZ,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at           TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_student_leave_school
    ON student_leave_applications(school_id);

CREATE INDEX IF NOT EXISTS idx_student_leave_student
    ON student_leave_applications(student_id);

CREATE INDEX IF NOT EXISTS idx_student_leave_section_status
    ON student_leave_applications(section_id, status);

-- ============================================================
-- 2. Timetable Substitutions
-- ============================================================
CREATE TABLE IF NOT EXISTS timetable_substitutions (
    id                      UUID        NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    school_id               UUID        NOT NULL,
    section_id              UUID        NOT NULL REFERENCES sections(id),
    period_id               UUID        NOT NULL REFERENCES timetable_periods(id),
    date                    DATE        NOT NULL,
    absent_teacher_id       UUID        NOT NULL REFERENCES staff(id),
    substitute_teacher_id   UUID        NOT NULL REFERENCES staff(id),
    reason                  TEXT,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ,

    -- Only one substitution per section + period + date
    CONSTRAINT uq_substitution_slot UNIQUE (section_id, period_id, date)
);

CREATE INDEX IF NOT EXISTS idx_substitution_school_date
    ON timetable_substitutions(school_id, date);

CREATE INDEX IF NOT EXISTS idx_substitution_substitute_date
    ON timetable_substitutions(substitute_teacher_id, date);


-- ─── from: V33__timetable_substitutions_created_by.sql ───
-- V33: Add missing created_by_id to timetable_substitutions
-- BaseEntity maps created_by_id for all tenant-scoped entities. This was omitted from V32.
ALTER TABLE timetable_substitutions
    ADD COLUMN IF NOT EXISTS created_by_id UUID REFERENCES staff(id);


-- ─── from: V34__attendance_approval.sql ───
-- V34: Add teacher self-attendance approval workflow.
-- Teachers now mark their own daily attendance; principal/admin reviews and approves.
-- Adding approval columns to the existing staff_attendance table.

ALTER TABLE staff_attendance
    ADD COLUMN IF NOT EXISTS approved        BOOLEAN     NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS approved_by_id  UUID        REFERENCES staff(id),
    ADD COLUMN IF NOT EXISTS approved_at     TIMESTAMPTZ;

CREATE INDEX IF NOT EXISTS idx_staff_attendance_pending
    ON staff_attendance (school_id, attendance_date)
    WHERE approved = FALSE;


-- ─── from: V35__ptm_slot_section.sql ───
-- V35: add optional section_id to ptm_slots so a slot can be tied to a class/section
-- When section_id is set, the backend broadcasts a PTM announcement to all parents in that class.

ALTER TABLE ptm_slots
    ADD COLUMN section_id UUID REFERENCES sections(id);


-- ─── from: V36__seed_leave_balances.sql ───
-- V36: Seed default leave balances for all existing staff who have none for the current year.
-- New staff get balances seeded via StaffService.seedLeaveBalances() on creation.
-- This migration backfills for staff created before that logic was added.
--
-- Default quotas (matches StaffService.DEFAULT_LEAVE_DAYS):
--   CASUAL=15, SICK=10, EARNED=12, MATERNITY=180, PATERNITY=7,
--   COMP_OFF=0, UNPAID=0, OTHER=0

INSERT INTO leave_balances (school_id, staff_id, leave_type, year, entitled_days, consumed_days)
SELECT
    s.school_id,
    s.id AS staff_id,
    lt.leave_type,
    EXTRACT(YEAR FROM NOW())::INT AS year,
    lt.entitled_days,
    0 AS consumed_days
FROM staff s
CROSS JOIN (
    VALUES
        ('CASUAL',    15),
        ('SICK',      10),
        ('EARNED',    12),
        ('MATERNITY', 180),
        ('PATERNITY', 7),
        ('COMP_OFF',  0),
        ('UNPAID',    0),
        ('OTHER',     0)
) AS lt(leave_type, entitled_days)
WHERE s.is_active = TRUE
  AND NOT EXISTS (
    SELECT 1
    FROM leave_balances lb
    WHERE lb.school_id = s.school_id
      AND lb.staff_id  = s.id
      AND lb.year      = EXTRACT(YEAR FROM NOW())::INT
  )
ON CONFLICT (school_id, staff_id, leave_type, year) DO NOTHING;


-- ─── from: V37__row_level_security.sql ───
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


-- ─── from: V38__approval_requests.sql ───
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


-- ─── from: V39__expense_approval.sql ───
-- V39 — Expense approval (audit fix #8).
-- Expenses were recorded and counted immediately with no approval. Route new expenses through
-- the maker-checker engine: they are created unapproved and excluded from totals until approved.

ALTER TABLE expenses
    ADD COLUMN IF NOT EXISTS approved       BOOLEAN NOT NULL DEFAULT TRUE,   -- backfill existing as approved
    ADD COLUMN IF NOT EXISTS approved_by_id UUID;

-- New rows are inserted approved=false by the application; only existing rows keep the TRUE default.
-- Pending-expense lookup for the approvals queue / reporting filter.
CREATE INDEX IF NOT EXISTS idx_expenses_school_approved
    ON expenses (school_id, approved);


-- ─── from: V40__cash_variance_review.sql ───
-- V40 — Cash-variance review/escalation (audit fix #9).
-- A drawer close with an over/short variance was recorded but never escalated. When the variance
-- exceeds the configured threshold the close now raises a maker-checker approval; the record stays
-- "unreviewed" until a checker (other than the person who closed the drawer) signs off.

ALTER TABLE cash_reconciliations
    ADD COLUMN IF NOT EXISTS variance_reviewed BOOLEAN NOT NULL DEFAULT TRUE,  -- existing rows grandfathered
    ADD COLUMN IF NOT EXISTS reviewed_by_id    UUID;

CREATE INDEX IF NOT EXISTS idx_cash_recon_unreviewed
    ON cash_reconciliations (school_id)
    WHERE variance_reviewed = FALSE;


-- ─── from: V41__visitor_pickup_auth.sql ───
-- V41 — Visitor student-pickup authorization (audit fix #16, child safety).
-- A student-pickup visit must be by a registered guardian (parent phone linked to the student);
-- otherwise the gate user must record an explicit override reason.

ALTER TABLE visitors
    ADD COLUMN IF NOT EXISTS student_pickup         BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS pickup_authorized      BOOLEAN,   -- NULL = not a pickup; TRUE/FALSE for pickups
    ADD COLUMN IF NOT EXISTS pickup_override_reason TEXT;


-- ─── from: V42__school_calendar.sql ───
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


-- ─── from: V43__classrooms.sql ───
-- Classrooms / rooms and their use in the timetable, enabling room double-booking detection.

CREATE TABLE classrooms (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id  UUID NOT NULL REFERENCES schools (id) ON DELETE CASCADE,
    name       VARCHAR(80)  NOT NULL,
    code       VARCHAR(30),
    building   VARCHAR(80),
    capacity   INT,
    room_type  VARCHAR(20) NOT NULL DEFAULT 'CLASSROOM',  -- CLASSROOM | LAB | LIBRARY | HALL | OTHER
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_classroom_name UNIQUE (school_id, name)
);
CREATE INDEX idx_classrooms_school ON classrooms (school_id);

-- A timetable slot may be held in a room. Nullable: not every period needs a fixed room.
ALTER TABLE timetable_entries ADD COLUMN room_id UUID REFERENCES classrooms (id) ON DELETE SET NULL;
CREATE INDEX idx_timetable_entries_room ON timetable_entries (room_id);

-- Tenant isolation for the new table (post-V37 tables must opt in explicitly).
ALTER TABLE classrooms ENABLE ROW LEVEL SECURITY;
ALTER TABLE classrooms FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_isolation ON classrooms;
CREATE POLICY tenant_isolation ON classrooms
    USING (
        NULLIF(current_setting('app.current_tenant', true), '') IS NULL
        OR school_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid
    )
    WITH CHECK (
        NULLIF(current_setting('app.current_tenant', true), '') IS NULL
        OR school_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid
    );


-- ─── from: V44__staff_profile.sql ───
-- Extended teacher/staff profile (identity, bank, professional details) stored as JSONB on the
-- existing staff row. No new table → existing staff RLS still applies.
ALTER TABLE staff ADD COLUMN IF NOT EXISTS profile JSONB NOT NULL DEFAULT '{}'::jsonb;


-- ─── from: V45__exam_planning.sql ───
-- Simplified exam planning: an exam can span several participating classes, carry a per-subject
-- examination schedule (date + time slots), and follow a configurable no-dues policy for admit cards.

-- Per-exam fee policy for admit-card generation:
--   BLOCK    – withhold the admit card while fees are outstanding (default, prior behaviour)
--   ALLOW    – always issue the admit card regardless of dues
--   OVERRIDE – withhold by default, but Principal/Admin may force-issue per student
ALTER TABLE exams ADD COLUMN IF NOT EXISTS fee_policy VARCHAR(10) NOT NULL DEFAULT 'BLOCK';

-- Classes participating in an exam (replaces the single optional class_id for multi-class exams).
CREATE TABLE exam_classes (
    exam_id   UUID NOT NULL REFERENCES exams (id) ON DELETE CASCADE,
    class_id  UUID NOT NULL REFERENCES school_classes (id) ON DELETE CASCADE,
    school_id UUID NOT NULL REFERENCES schools (id) ON DELETE CASCADE,
    PRIMARY KEY (exam_id, class_id)
);
CREATE INDEX idx_exam_classes_exam ON exam_classes (exam_id);
CREATE INDEX idx_exam_classes_school ON exam_classes (school_id);

-- Examination timetable: one row per subject sitting (date + time window).
CREATE TABLE exam_schedule (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id  UUID NOT NULL REFERENCES schools (id) ON DELETE CASCADE,
    exam_id    UUID NOT NULL REFERENCES exams (id) ON DELETE CASCADE,
    subject_id UUID NOT NULL REFERENCES subjects (id) ON DELETE CASCADE,
    exam_date  DATE NOT NULL,
    start_time TIME,
    end_time   TIME,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_exam_schedule_subject UNIQUE (exam_id, subject_id)
);
CREATE INDEX idx_exam_schedule_exam ON exam_schedule (exam_id);
CREATE INDEX idx_exam_schedule_school ON exam_schedule (school_id);

-- Tenant isolation for the new tables (post-V37 tables must opt in explicitly).
ALTER TABLE exam_classes ENABLE ROW LEVEL SECURITY;
ALTER TABLE exam_classes FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_isolation ON exam_classes;
CREATE POLICY tenant_isolation ON exam_classes
    USING (
        NULLIF(current_setting('app.current_tenant', true), '') IS NULL
        OR school_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid
    )
    WITH CHECK (
        NULLIF(current_setting('app.current_tenant', true), '') IS NULL
        OR school_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid
    );

ALTER TABLE exam_schedule ENABLE ROW LEVEL SECURITY;
ALTER TABLE exam_schedule FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_isolation ON exam_schedule;
CREATE POLICY tenant_isolation ON exam_schedule
    USING (
        NULLIF(current_setting('app.current_tenant', true), '') IS NULL
        OR school_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid
    )
    WITH CHECK (
        NULLIF(current_setting('app.current_tenant', true), '') IS NULL
        OR school_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid
    );

