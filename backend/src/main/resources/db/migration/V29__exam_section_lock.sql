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
