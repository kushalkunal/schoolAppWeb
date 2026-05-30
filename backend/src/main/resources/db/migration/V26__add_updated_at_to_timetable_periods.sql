-- =====================================================================================
-- V26 — Back-fill updated_at audit column on timetable_periods
-- =====================================================================================
-- timetable_periods was created in V9 without updated_at, while BaseEntity requires it.
-- =====================================================================================

ALTER TABLE timetable_periods ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW();
