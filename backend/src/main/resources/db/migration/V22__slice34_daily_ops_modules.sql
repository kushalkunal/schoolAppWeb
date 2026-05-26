-- =====================================================================================
-- Slice 34 — daily-ops gap fillers. One migration creates the storage for every module
-- registered in Slice 33's feature-flag catalog. Each table is school_id-scoped (multi-
-- tenant) and FK-cascaded so deleting a school cleans up its rows.
-- =====================================================================================

-- ---------- Visitor management ----------
CREATE TABLE visitors (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id       UUID NOT NULL REFERENCES schools(id) ON DELETE CASCADE,
    name            VARCHAR(120) NOT NULL,
    phone           VARCHAR(30),
    purpose         VARCHAR(255),
    host_staff_id   UUID REFERENCES staff(id) ON DELETE SET NULL,
    host_student_id UUID REFERENCES students(id) ON DELETE SET NULL,
    badge_number    VARCHAR(40),
    photo_url       TEXT,
    in_at           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    out_at          TIMESTAMPTZ,
    notes           TEXT,
    created_by_id   UUID
);
CREATE INDEX idx_visitors_school_in  ON visitors(school_id, in_at DESC);
CREATE INDEX idx_visitors_open       ON visitors(school_id) WHERE out_at IS NULL;

-- ---------- Day-end cash reconciliation ----------
-- The "drawer" is a per-day-per-staff accumulator; payments already record their mode, so we
-- aggregate at close-time and store the expected-vs-counted variance.
CREATE TABLE cash_reconciliations (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id       UUID NOT NULL REFERENCES schools(id) ON DELETE CASCADE,
    closed_by_id    UUID NOT NULL,
    closed_on_date  DATE NOT NULL,
    expected_cash_paise   BIGINT NOT NULL,
    expected_upi_paise    BIGINT NOT NULL,
    expected_cheque_paise BIGINT NOT NULL,
    expected_other_paise  BIGINT NOT NULL,
    counted_cash_paise    BIGINT NOT NULL,
    counted_upi_paise     BIGINT NOT NULL,
    counted_cheque_paise  BIGINT NOT NULL,
    counted_other_paise   BIGINT NOT NULL,
    variance_paise        BIGINT NOT NULL,
    notes           TEXT,
    closed_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (school_id, closed_on_date, closed_by_id)
);
CREATE INDEX idx_cash_recon_school_date ON cash_reconciliations(school_id, closed_on_date DESC);

-- ---------- Expense tracking ----------
CREATE TABLE expense_categories (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id   UUID NOT NULL REFERENCES schools(id) ON DELETE CASCADE,
    name        VARCHAR(80) NOT NULL,
    active      BOOLEAN NOT NULL DEFAULT TRUE,
    UNIQUE (school_id, name)
);
CREATE TABLE expenses (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id       UUID NOT NULL REFERENCES schools(id) ON DELETE CASCADE,
    category_id     UUID REFERENCES expense_categories(id) ON DELETE SET NULL,
    amount_paise    BIGINT NOT NULL CHECK (amount_paise >= 0),
    spent_on        DATE NOT NULL,
    vendor          VARCHAR(120),
    description     TEXT,
    receipt_url     TEXT,
    payment_mode    VARCHAR(20),                   -- CASH / UPI / CHEQUE / CARD / BANK
    recorded_by_id  UUID,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_expenses_school_date ON expenses(school_id, spent_on DESC);

-- ---------- Incident / behaviour log ----------
CREATE TABLE incidents (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id       UUID NOT NULL REFERENCES schools(id) ON DELETE CASCADE,
    student_id      UUID NOT NULL REFERENCES students(id) ON DELETE CASCADE,
    severity        VARCHAR(15) NOT NULL,           -- MINOR / MAJOR / SEVERE
    incident_type   VARCHAR(40) NOT NULL,           -- MERIT / DEMERIT / DISCIPLINE / ACADEMIC
    points          INTEGER NOT NULL DEFAULT 0,     -- + for merit, - for demerit
    occurred_on     DATE NOT NULL,
    description     TEXT NOT NULL,
    action_taken    TEXT,
    reported_by_id  UUID NOT NULL,
    parent_notified BOOLEAN NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_incidents_student ON incidents(student_id, occurred_on DESC);
CREATE INDEX idx_incidents_school  ON incidents(school_id, occurred_on DESC);

-- ---------- Infirmary visit log ----------
CREATE TABLE infirmary_visits (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id       UUID NOT NULL REFERENCES schools(id) ON DELETE CASCADE,
    student_id      UUID NOT NULL REFERENCES students(id) ON DELETE CASCADE,
    visited_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    complaint       TEXT NOT NULL,
    treatment       TEXT,
    medicine_given  TEXT,
    temperature_c   NUMERIC(4,1),
    pulse           INTEGER,
    sent_home       BOOLEAN NOT NULL DEFAULT FALSE,
    parent_notified BOOLEAN NOT NULL DEFAULT FALSE,
    recorded_by_id  UUID NOT NULL,
    notes           TEXT
);
CREATE INDEX idx_infirmary_student ON infirmary_visits(student_id, visited_at DESC);
CREATE INDEX idx_infirmary_school  ON infirmary_visits(school_id, visited_at DESC);

-- Per-student static medical record — surfaced on the marking screen + infirmary screen.
CREATE TABLE student_medical_records (
    student_id          UUID PRIMARY KEY REFERENCES students(id) ON DELETE CASCADE,
    school_id           UUID NOT NULL REFERENCES schools(id) ON DELETE CASCADE,
    blood_group         VARCHAR(5),
    allergies           TEXT,
    chronic_conditions  TEXT,
    medications         TEXT,
    emergency_contact   VARCHAR(120),
    emergency_phone     VARCHAR(30),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- ---------- Student document vault ----------
-- A V5 students-only table exists; this one is a generic per-student multi-doc bucket.
CREATE TABLE student_vault_documents (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id       UUID NOT NULL REFERENCES schools(id) ON DELETE CASCADE,
    student_id      UUID NOT NULL REFERENCES students(id) ON DELETE CASCADE,
    doc_type        VARCHAR(50) NOT NULL,           -- AADHAAR, BIRTH_CERT, PREV_MARKSHEET, …
    file_name       VARCHAR(255) NOT NULL,
    file_url        TEXT NOT NULL,
    mime_type       VARCHAR(120),
    size_bytes      BIGINT,
    uploaded_by_id  UUID NOT NULL,
    uploaded_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    notes           TEXT
);
CREATE INDEX idx_vault_student ON student_vault_documents(student_id, uploaded_at DESC);

-- ---------- Inventory low-stock thresholds + alerts ----------
-- Tiny extension to existing inventory tables. We only add a threshold column + a denormalised
-- "alerted_at" timestamp so the cron doesn't re-fire every tick.
ALTER TABLE inventory_items
    ADD COLUMN IF NOT EXISTS low_stock_threshold INTEGER,
    ADD COLUMN IF NOT EXISTS low_stock_alerted_at TIMESTAMPTZ;

-- ---------- Parent-teacher meeting (PTM) scheduling ----------
CREATE TABLE ptm_slots (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id       UUID NOT NULL REFERENCES schools(id) ON DELETE CASCADE,
    teacher_id      UUID NOT NULL REFERENCES staff(id) ON DELETE CASCADE,
    slot_date       DATE NOT NULL,
    start_time      TIME NOT NULL,
    end_time        TIME NOT NULL,
    capacity        INTEGER NOT NULL DEFAULT 1,
    booked_count    INTEGER NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (teacher_id, slot_date, start_time)
);
CREATE INDEX idx_ptm_slots_school ON ptm_slots(school_id, slot_date);

CREATE TABLE ptm_bookings (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    slot_id         UUID NOT NULL REFERENCES ptm_slots(id) ON DELETE CASCADE,
    school_id       UUID NOT NULL REFERENCES schools(id) ON DELETE CASCADE,
    student_id      UUID NOT NULL REFERENCES students(id) ON DELETE CASCADE,
    parent_id       UUID REFERENCES parents(id) ON DELETE SET NULL,
    status          VARCHAR(15) NOT NULL DEFAULT 'CONFIRMED',  -- CONFIRMED / CANCELLED / COMPLETED
    notes           TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (slot_id, student_id)
);
CREATE INDEX idx_ptm_bookings_slot ON ptm_bookings(slot_id);
CREATE INDEX idx_ptm_bookings_student ON ptm_bookings(student_id, created_at DESC);

-- ---------- Unified messages timeline ----------
-- A denormalised reader-friendly view of every outbound message (WA + email + SMS).
-- notification_log already records sends; this table is a per-student inbox cache that the
-- /inbox page reads in O(log n). The CircularService and ParentNotificationService write here.
CREATE TABLE parent_messages (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id       UUID NOT NULL REFERENCES schools(id) ON DELETE CASCADE,
    student_id      UUID REFERENCES students(id) ON DELETE CASCADE,
    parent_id       UUID REFERENCES parents(id) ON DELETE SET NULL,
    channel         VARCHAR(15) NOT NULL,           -- WHATSAPP / EMAIL / SMS
    category        VARCHAR(40) NOT NULL,           -- mirrors FeatureKey.PARENT_NOTIFY_*
    subject         VARCHAR(255),
    body            TEXT NOT NULL,
    media_url       TEXT,
    status          VARCHAR(15) NOT NULL DEFAULT 'SENT',  -- SENT / DELIVERED / READ / FAILED
    sent_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    delivered_at    TIMESTAMPTZ,
    read_at         TIMESTAMPTZ
);
CREATE INDEX idx_parent_msg_student   ON parent_messages(student_id, sent_at DESC);
CREATE INDEX idx_parent_msg_school    ON parent_messages(school_id, sent_at DESC);

-- ---------- Persistent alerts feed ----------
-- The analytics module's `alerts` table already stores in-the-now alerts; we add a generic
-- "user_alerts" view-state so each staff member can mark them read.
CREATE TABLE alert_dismissals (
    alert_id        UUID NOT NULL,                  -- analytics.alerts.id; loose FK to avoid module coupling
    staff_id        UUID NOT NULL REFERENCES staff(id) ON DELETE CASCADE,
    dismissed_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (alert_id, staff_id)
);
