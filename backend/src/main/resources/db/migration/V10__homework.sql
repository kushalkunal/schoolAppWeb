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
