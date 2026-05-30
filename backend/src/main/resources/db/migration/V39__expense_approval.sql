-- V39 — Expense approval (audit fix #8).
-- Expenses were recorded and counted immediately with no approval. Route new expenses through
-- the maker-checker engine: they are created unapproved and excluded from totals until approved.

ALTER TABLE expenses
    ADD COLUMN IF NOT EXISTS approved       BOOLEAN NOT NULL DEFAULT TRUE,   -- backfill existing as approved
    ADD COLUMN IF NOT EXISTS approved_by_id UUID;

-- New rows are inserted approved=false by the application; only existing rows keep the TRUE default.
-- Pending-expense lookup for the approvals queue / reporting filter.
CREATE INDEX IF NOT EXISTS idx_expenses_school_approved
    ON expenses (school_id, approved);
