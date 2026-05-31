-- Extended teacher/staff profile (identity, bank, professional details) stored as JSONB on the
-- existing staff row. No new table → existing staff RLS still applies.
ALTER TABLE staff ADD COLUMN IF NOT EXISTS profile JSONB NOT NULL DEFAULT '{}'::jsonb;
