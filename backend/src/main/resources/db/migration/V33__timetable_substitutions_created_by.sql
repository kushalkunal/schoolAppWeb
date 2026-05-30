-- V33: Add missing created_by_id to timetable_substitutions
-- BaseEntity maps created_by_id for all tenant-scoped entities. This was omitted from V32.
ALTER TABLE timetable_substitutions
    ADD COLUMN IF NOT EXISTS created_by_id UUID REFERENCES staff(id);
