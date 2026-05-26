-- =====================================================================================
-- Slice 16 — HR module: staff attendance, leave management, payroll
-- =====================================================================================
-- Design notes:
--   1. staff_attendance is per-day per-staff (unique). Bulk marking is N rows in one txn.
--   2. leave_applications is the source of truth for "is staff X on approved leave on date Y"
--      — the payroll calc joins this to deduct LWP days from the gross.
--   3. salary_structures supports per-role baselines + per-staff overrides via the
--      staff_id column (NULL = role default, NOT NULL = staff-specific).
--   4. Payslips are immutable snapshots — once generated, schema does not allow updates
--      (enforced in service; no trigger needed).
-- =====================================================================================

-- ---------- staff_attendance ----------
CREATE TABLE staff_attendance (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id       UUID NOT NULL REFERENCES schools(id) ON DELETE CASCADE,
    staff_id        UUID NOT NULL REFERENCES staff(id) ON DELETE CASCADE,
    attendance_date DATE NOT NULL,
    status          VARCHAR(20) NOT NULL,                -- PRESENT | ABSENT | HALF_DAY | LEAVE | HOLIDAY | LATE
    notes           TEXT,
    marked_by_id    UUID,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE(school_id, staff_id, attendance_date)
);
CREATE INDEX idx_staff_attendance_date  ON staff_attendance(school_id, attendance_date);
CREATE INDEX idx_staff_attendance_staff ON staff_attendance(staff_id);


-- ---------- leave_applications ----------
CREATE TABLE leave_applications (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id       UUID NOT NULL REFERENCES schools(id) ON DELETE CASCADE,
    staff_id        UUID NOT NULL REFERENCES staff(id) ON DELETE CASCADE,
    -- CASUAL | SICK | EARNED | UNPAID | MATERNITY | PATERNITY | COMP_OFF | OTHER
    leave_type      VARCHAR(20) NOT NULL,
    start_date      DATE NOT NULL,
    end_date        DATE NOT NULL,
    -- Floating-point days handles half-day applications (0.5, 1.5 etc).
    days            NUMERIC(5,2) NOT NULL,
    reason          TEXT,
    -- SUBMITTED → APPROVED | REJECTED | CANCELLED
    status          VARCHAR(15) NOT NULL DEFAULT 'SUBMITTED',
    decided_by_id   UUID,
    decided_at      TIMESTAMPTZ,
    decision_note   TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by_id   UUID,
    CONSTRAINT leave_dates_valid CHECK (end_date >= start_date),
    CONSTRAINT leave_days_positive CHECK (days > 0)
);
CREATE INDEX idx_leave_app_staff  ON leave_applications(staff_id, start_date);
CREATE INDEX idx_leave_app_school ON leave_applications(school_id, status);


-- ---------- leave_balances ----------
-- Per-staff, per-leave-type entitlement and consumption. The HR admin maintains this row.
-- LeaveService.approve() decrements `consumed_days`. A negative balance is allowed (carry-over
-- + unpaid) — service surfaces it as a warning, not a hard error.
CREATE TABLE leave_balances (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id       UUID NOT NULL REFERENCES schools(id) ON DELETE CASCADE,
    staff_id        UUID NOT NULL REFERENCES staff(id) ON DELETE CASCADE,
    leave_type      VARCHAR(20) NOT NULL,
    year            INT NOT NULL,                        -- e.g. 2026
    entitled_days   NUMERIC(5,2) NOT NULL,
    consumed_days   NUMERIC(5,2) NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE(school_id, staff_id, leave_type, year)
);


-- ---------- salary_structures ----------
-- Either role-default (staff_id NULL) OR staff-specific (staff_id set). When generating
-- a payslip, the service looks for the staff-specific row first, falling back to role.
CREATE TABLE salary_structures (
    id                       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id                UUID NOT NULL REFERENCES schools(id) ON DELETE CASCADE,
    role                     VARCHAR(30) NOT NULL,        -- StaffRole enum value
    staff_id                 UUID REFERENCES staff(id) ON DELETE CASCADE,
    basic_paise              BIGINT NOT NULL DEFAULT 0,
    hra_paise                BIGINT NOT NULL DEFAULT 0,    -- house rent allowance
    da_paise                 BIGINT NOT NULL DEFAULT 0,    -- dearness allowance
    special_allowance_paise  BIGINT NOT NULL DEFAULT 0,
    other_allowance_paise    BIGINT NOT NULL DEFAULT 0,
    pf_percent               NUMERIC(5,2) NOT NULL DEFAULT 12.00,   -- typical India default
    esi_percent              NUMERIC(5,2) NOT NULL DEFAULT 0.75,
    professional_tax_paise   BIGINT NOT NULL DEFAULT 0,    -- per-state flat amount
    notes                    TEXT,
    effective_from           DATE NOT NULL,
    effective_until          DATE,                         -- NULL = open-ended
    active                   BOOLEAN NOT NULL DEFAULT TRUE,
    created_at               TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at               TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by_id            UUID
);
CREATE INDEX idx_salary_struct_school ON salary_structures(school_id);
CREATE INDEX idx_salary_struct_staff  ON salary_structures(staff_id) WHERE staff_id IS NOT NULL;
CREATE INDEX idx_salary_struct_role   ON salary_structures(school_id, role) WHERE staff_id IS NULL;


-- ---------- payslips ----------
-- Immutable monthly snapshots — once a payslip is generated, the row is never updated.
-- Re-generation creates a new row with version+1 (used for corrections / re-issues).
CREATE TABLE payslips (
    id                       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id                UUID NOT NULL REFERENCES schools(id) ON DELETE CASCADE,
    staff_id                 UUID NOT NULL REFERENCES staff(id) ON DELETE CASCADE,
    pay_period_year          INT NOT NULL,
    pay_period_month         INT NOT NULL,                -- 1-12
    version                  INT NOT NULL DEFAULT 1,
    working_days             NUMERIC(5,2) NOT NULL,
    leave_days_paid          NUMERIC(5,2) NOT NULL DEFAULT 0,
    leave_days_unpaid        NUMERIC(5,2) NOT NULL DEFAULT 0,
    gross_paise              BIGINT NOT NULL,
    deductions_paise         BIGINT NOT NULL,
    net_paise                BIGINT NOT NULL,
    breakdown_json           JSONB NOT NULL,             -- full snapshot of the calc inputs
    pdf_url                  TEXT,
    generated_at             TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    generated_by_id          UUID,
    UNIQUE(school_id, staff_id, pay_period_year, pay_period_month, version)
);
CREATE INDEX idx_payslips_staff  ON payslips(staff_id, pay_period_year DESC, pay_period_month DESC);
CREATE INDEX idx_payslips_school ON payslips(school_id, pay_period_year, pay_period_month);
