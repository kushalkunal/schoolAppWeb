-- V34: Add teacher self-attendance approval workflow.
-- Teachers now mark their own daily attendance; principal/admin reviews and approves.
-- Adding approval columns to the existing staff_attendance table.

ALTER TABLE staff_attendance
    ADD COLUMN IF NOT EXISTS approved        BOOLEAN     NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS approved_by_id  UUID        REFERENCES staff(id),
    ADD COLUMN IF NOT EXISTS approved_at     TIMESTAMPTZ;

CREATE INDEX IF NOT EXISTS idx_staff_attendance_pending
    ON staff_attendance (school_id, attendance_date)
    WHERE approved = FALSE;
