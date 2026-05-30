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
