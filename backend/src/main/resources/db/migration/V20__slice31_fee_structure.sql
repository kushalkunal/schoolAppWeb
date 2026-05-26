-- =====================================================================================
-- Slice 31 — Fee structure matrix (per class × fee head × term)
-- =====================================================================================
-- Problem: schools have different fee amounts per class. Admins shouldn't manually
-- create 2,400 invoices per term (800 students × 3 heads). This migration adds the
-- matrix data model so the bulk invoice-generator (Slice 31c) can populate
-- fee_invoices for every enrolled student in one click.
--
-- Design:
--   1. fee_structure_versions  — one DRAFT/ACTIVE/ARCHIVED snapshot per academic year
--   2. fee_structure_terms     — optional N-term split (annual = no rows)
--   3. fee_structure_rows      — the matrix cells; one row per (class × head × term)
--
-- Idempotency: re-running invoice generation for the same (version, term, student)
-- triggers a UNIQUE constraint at the fee_invoices level via a partial index added
-- below — preventing the most common "I clicked it twice" double-billing bug.
-- =====================================================================================

CREATE TABLE fee_structure_versions (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id           UUID NOT NULL REFERENCES schools(id) ON DELETE CASCADE,
    academic_year_id    UUID NOT NULL REFERENCES academic_years(id) ON DELETE CASCADE,
    name                VARCHAR(120) NOT NULL,
    status              VARCHAR(15) NOT NULL DEFAULT 'DRAFT',   -- DRAFT | ACTIVE | ARCHIVED
    notes               TEXT,
    activated_at        TIMESTAMPTZ,
    archived_at         TIMESTAMPTZ,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by_id       UUID,
    -- A school can have many DRAFT versions but only one ACTIVE per academic year.
    -- Enforced at the application layer (FeeStructureService.activate) since Postgres
    -- partial unique indexes on enum values are noisy and this is a low-write surface.
    UNIQUE(school_id, academic_year_id, name)
);
CREATE INDEX idx_fee_struct_versions_school ON fee_structure_versions(school_id);
CREATE INDEX idx_fee_struct_versions_active ON fee_structure_versions(school_id, academic_year_id)
    WHERE status = 'ACTIVE';

-- Optional term split. Schools that bill annually skip this entirely; row count = 0
-- → invoice generator treats the structure as single-term-due-on-academic-year-start.
CREATE TABLE fee_structure_terms (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    version_id          UUID NOT NULL REFERENCES fee_structure_versions(id) ON DELETE CASCADE,
    term_number         INT NOT NULL,                          -- 1, 2, 3 …
    name                VARCHAR(80) NOT NULL,                  -- "Term 1 — Apr-Jul"
    start_date          DATE NOT NULL,
    end_date            DATE NOT NULL,
    due_date            DATE NOT NULL,                         -- when this term's invoices are due
    UNIQUE(version_id, term_number),
    CONSTRAINT fee_struct_term_dates CHECK (end_date >= start_date AND due_date >= start_date)
);
CREATE INDEX idx_fee_struct_terms_version ON fee_structure_terms(version_id);

-- The matrix cells. (class × head × term) — term is nullable for annual structures.
-- Amount = 0 is legal (some heads only apply to some classes; you could either
-- omit the row OR insert a zero row — both mean "nothing to bill"; we recommend
-- omitting for cleanliness but tolerate zeros).
CREATE TABLE fee_structure_rows (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    version_id          UUID NOT NULL REFERENCES fee_structure_versions(id) ON DELETE CASCADE,
    class_id            UUID NOT NULL REFERENCES school_classes(id) ON DELETE CASCADE,
    fee_head_id         UUID NOT NULL REFERENCES fee_heads(id) ON DELETE CASCADE,
    term_number         INT,                                   -- NULL = annual
    amount_paise        BIGINT NOT NULL,
    -- True for fees the student opts into (transport, library) — generator skips these
    -- unless a per-student opt-in record exists (deferred to a later slice; default off).
    is_optional         BOOLEAN NOT NULL DEFAULT FALSE,
    UNIQUE(version_id, class_id, fee_head_id, term_number),
    CONSTRAINT fee_struct_row_amount_non_negative CHECK (amount_paise >= 0)
);
CREATE INDEX idx_fee_struct_rows_version ON fee_structure_rows(version_id);
CREATE INDEX idx_fee_struct_rows_class   ON fee_structure_rows(class_id);


-- =====================================================================================
-- Idempotency guard on fee_invoices: prevent the generator from issuing the same
-- (student, head, term, source-version) invoice twice. We add a NULLABLE column
-- pointing back to the structure row that birthed each invoice; the partial unique
-- index only constrains generator-created invoices, leaving manually-created
-- invoices (opening balances, ad-hoc) unaffected.
-- =====================================================================================
ALTER TABLE fee_invoices
    ADD COLUMN structure_version_id UUID REFERENCES fee_structure_versions(id) ON DELETE SET NULL,
    ADD COLUMN structure_term_number INT;

CREATE UNIQUE INDEX uq_fee_invoices_per_structure
    ON fee_invoices(school_id, student_id, fee_head_id, structure_version_id, structure_term_number)
    WHERE structure_version_id IS NOT NULL;


-- =====================================================================================
-- Feature key + plan mapping. Reuses existing seeding pattern from V14.
-- =====================================================================================
INSERT INTO features (feature_key, name, description, default_enabled, category) VALUES
    ('FEE_STRUCTURE',
     'Fee structure matrix',
     'Per-class fee structure with one-click bulk invoice generation',
     FALSE, 'Finance')
ON CONFLICT (feature_key) DO NOTHING;

WITH p AS (SELECT id, code FROM plans)
INSERT INTO plan_features (plan_id, feature_key)
SELECT p.id, f.feature_key
FROM p
CROSS JOIN LATERAL (VALUES
    ('STARTER',    'FEE_STRUCTURE'),
    ('GROWTH',     'FEE_STRUCTURE'),
    ('ENTERPRISE', 'FEE_STRUCTURE')
) AS f(plan_code, feature_key)
WHERE p.code = f.plan_code
ON CONFLICT DO NOTHING;
