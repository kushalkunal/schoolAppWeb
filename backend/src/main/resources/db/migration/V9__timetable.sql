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
