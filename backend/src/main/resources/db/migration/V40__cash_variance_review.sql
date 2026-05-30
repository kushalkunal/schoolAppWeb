-- V40 — Cash-variance review/escalation (audit fix #9).
-- A drawer close with an over/short variance was recorded but never escalated. When the variance
-- exceeds the configured threshold the close now raises a maker-checker approval; the record stays
-- "unreviewed" until a checker (other than the person who closed the drawer) signs off.

ALTER TABLE cash_reconciliations
    ADD COLUMN IF NOT EXISTS variance_reviewed BOOLEAN NOT NULL DEFAULT TRUE,  -- existing rows grandfathered
    ADD COLUMN IF NOT EXISTS reviewed_by_id    UUID;

CREATE INDEX IF NOT EXISTS idx_cash_recon_unreviewed
    ON cash_reconciliations (school_id)
    WHERE variance_reviewed = FALSE;
