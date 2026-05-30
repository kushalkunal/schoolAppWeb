-- V36: Seed default leave balances for all existing staff who have none for the current year.
-- New staff get balances seeded via StaffService.seedLeaveBalances() on creation.
-- This migration backfills for staff created before that logic was added.
--
-- Default quotas (matches StaffService.DEFAULT_LEAVE_DAYS):
--   CASUAL=15, SICK=10, EARNED=12, MATERNITY=180, PATERNITY=7,
--   COMP_OFF=0, UNPAID=0, OTHER=0

INSERT INTO leave_balances (school_id, staff_id, leave_type, year, entitled_days, consumed_days)
SELECT
    s.school_id,
    s.id AS staff_id,
    lt.leave_type,
    EXTRACT(YEAR FROM NOW())::INT AS year,
    lt.entitled_days,
    0 AS consumed_days
FROM staff s
CROSS JOIN (
    VALUES
        ('CASUAL',    15),
        ('SICK',      10),
        ('EARNED',    12),
        ('MATERNITY', 180),
        ('PATERNITY', 7),
        ('COMP_OFF',  0),
        ('UNPAID',    0),
        ('OTHER',     0)
) AS lt(leave_type, entitled_days)
WHERE s.is_active = TRUE
  AND NOT EXISTS (
    SELECT 1
    FROM leave_balances lb
    WHERE lb.school_id = s.school_id
      AND lb.staff_id  = s.id
      AND lb.year      = EXTRACT(YEAR FROM NOW())::INT
  )
ON CONFLICT (school_id, staff_id, leave_type, year) DO NOTHING;
