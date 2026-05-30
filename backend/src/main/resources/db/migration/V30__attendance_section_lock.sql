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
