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
