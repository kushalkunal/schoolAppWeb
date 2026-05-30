-- =====================================================================================
-- V24 — Teacher invite / onboarding via email
--
-- Adds must_reset_password so admins can pre-set a temp password for a teacher
-- and the teacher is forced to change it on first login.
-- =====================================================================================

ALTER TABLE staff
    ADD COLUMN IF NOT EXISTS must_reset_password BOOLEAN NOT NULL DEFAULT FALSE;
