-- =====================================================================================
-- Slice 15 — Fee depth: late-fee automation, discounts/concessions, refunds/adjustments,
-- multi-installment plans, GST per fee head.
-- =====================================================================================
-- Design notes:
--   1. All money values stay in PAISE (BIGINT). Never use DOUBLE for currency.
--   2. fee_adjustments is the audit ledger — every late_fee, discount-applied, refund,
--      manual write-off lands here. Invoices snapshot their state by referencing the
--      head adjustment ids, so /payments/{id}/refund preserves history.
--   3. Per-head LATE_FEE config is intentionally on fee_heads (not a global setting):
--      tuition fee can have ₹10/day cap ₹500, transport can have ₹0 (no late fee).
-- =====================================================================================

-- ---------- 1. fee_discounts ----------
-- Tracks structural concessions (sibling discount, merit scholarship, financial-aid waiver).
-- Applied to a specific student over a date range. The invoice-generation service consults
-- this table at compute time; no row mutations on invoices themselves.
CREATE TABLE fee_discounts (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id        UUID NOT NULL REFERENCES schools(id) ON DELETE CASCADE,
    student_id       UUID NOT NULL REFERENCES students(id) ON DELETE CASCADE,
    fee_head_id      UUID REFERENCES fee_heads(id) ON DELETE CASCADE,
    -- NULL means the discount applies to ALL heads (e.g. blanket sibling discount).
    discount_type    VARCHAR(30) NOT NULL,
    -- SIBLING | SCHOLARSHIP | FINANCIAL_AID | STAFF_KID | EARLY_BIRD | CUSTOM
    percent          NUMERIC(5,2),                -- e.g. 10.00 = 10%
    fixed_paise      BIGINT,                      -- alternative to percent; mutually exclusive
    valid_from       DATE NOT NULL,
    valid_until      DATE,                        -- NULL = open-ended
    reason           TEXT,
    approved_by_id   UUID,                        -- staff who approved
    active           BOOLEAN NOT NULL DEFAULT TRUE,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by_id    UUID,
    CONSTRAINT fee_discount_amount_xor CHECK (
        (percent IS NOT NULL AND fixed_paise IS NULL)
        OR (percent IS NULL AND fixed_paise IS NOT NULL)
    )
);
CREATE INDEX idx_fee_discounts_school ON fee_discounts(school_id);
CREATE INDEX idx_fee_discounts_student ON fee_discounts(student_id) WHERE active;


-- ---------- 2. fee_adjustments ----------
-- Append-only audit ledger of every non-payment mutation to an invoice.
CREATE TABLE fee_adjustments (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id        UUID NOT NULL REFERENCES schools(id) ON DELETE CASCADE,
    invoice_id       UUID REFERENCES fee_invoices(id) ON DELETE CASCADE,
    payment_id       UUID REFERENCES fee_payments(id) ON DELETE SET NULL,
    -- LATE_FEE | DISCOUNT_APPLIED | REFUND | WRITE_OFF | MANUAL_CREDIT | MANUAL_DEBIT
    adjustment_type  VARCHAR(30) NOT NULL,
    amount_paise     BIGINT NOT NULL,             -- positive: increases due; negative: reduces
    reason           TEXT,
    approved_by_id   UUID,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by_id    UUID
);
CREATE INDEX idx_fee_adjustments_school ON fee_adjustments(school_id);
CREATE INDEX idx_fee_adjustments_invoice ON fee_adjustments(invoice_id);
CREATE INDEX idx_fee_adjustments_payment ON fee_adjustments(payment_id);


-- ---------- 3. fee_installment_plans / fee_installments ----------
-- An installment plan supersedes a parent invoice with N child invoices, each with its own
-- due date. The parent's status flips to SUPERSEDED so the dashboard / defaulters list
-- ignores it (queries already filter by status).
CREATE TABLE fee_installment_plans (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id        UUID NOT NULL REFERENCES schools(id) ON DELETE CASCADE,
    parent_invoice_id UUID NOT NULL REFERENCES fee_invoices(id) ON DELETE CASCADE,
    installment_count INT NOT NULL,
    notes            TEXT,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by_id    UUID,
    UNIQUE(parent_invoice_id)
);

CREATE TABLE fee_installments (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    plan_id          UUID NOT NULL REFERENCES fee_installment_plans(id) ON DELETE CASCADE,
    child_invoice_id UUID NOT NULL REFERENCES fee_invoices(id) ON DELETE CASCADE,
    sequence_no      INT NOT NULL,                -- 1, 2, 3 …
    due_date         DATE NOT NULL,
    amount_paise     BIGINT NOT NULL,
    UNIQUE(plan_id, sequence_no)
);


-- ---------- 4. Late-fee configuration on fee_heads ----------
ALTER TABLE fee_heads
    ADD COLUMN gst_percent              NUMERIC(5,2) NOT NULL DEFAULT 0,
    ADD COLUMN late_fee_paise_per_day   BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN late_fee_grace_days      INT NOT NULL DEFAULT 0,
    ADD COLUMN late_fee_cap_paise       BIGINT;     -- NULL = uncapped


-- ---------- 5. Mutations on fee_invoices ----------
ALTER TABLE fee_invoices
    ADD COLUMN late_fee_applied_paise   BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN last_late_fee_applied_at TIMESTAMPTZ,
    ADD COLUMN discount_applied_paise   BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN gst_paise                BIGINT NOT NULL DEFAULT 0,
    -- New status values: SUPERSEDED (parent of an installment plan), REFUNDED (everything reversed).
    -- The enum is stored as VARCHAR in the existing schema (see V1), so no enum migration needed —
    -- just remember to update InvoiceStatus.java in lock-step.
    ADD COLUMN superseded_by_plan_id    UUID REFERENCES fee_installment_plans(id) ON DELETE SET NULL;


-- ---------- 6. Per-school GSTIN ----------
-- Stored in schools.settings JSONB under key "gstin" so we don't need a new column.
-- Receipts read it via DocumentService.

-- =====================================================================================
-- No new feature_keys here — Slice 12's V14 already seeded LATE_FEE_AUTOMATION,
-- FEE_DISCOUNTS, FEE_REFUNDS, FEE_INSTALLMENTS, FEE_GST. The plan mappings are in V14.
-- =====================================================================================
