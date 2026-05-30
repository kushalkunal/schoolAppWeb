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
