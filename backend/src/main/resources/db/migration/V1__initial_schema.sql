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
