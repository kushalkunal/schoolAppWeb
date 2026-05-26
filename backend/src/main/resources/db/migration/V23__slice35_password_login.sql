-- =====================================================================================
-- Slice 35 — Optional password login alongside OTP.
--
-- Why: schools that don't want OTP-every-time asked for "remember-me" style passwords.
-- The OTP flow stays (still required for first signup verification + password reset);
-- once a user sets a password they can choose Password OR OTP at login.
--
-- BCrypt hashes are 60 chars (work-factor 10 by default); we leave room for stronger
-- algorithms (Argon2, work-factor bumps) by reserving 100 chars.
-- =====================================================================================

ALTER TABLE staff
    ADD COLUMN IF NOT EXISTS password_hash       VARCHAR(100),
    -- Track when we last verified the identifier so cooldown / abuse-detection has data.
    ADD COLUMN IF NOT EXISTS identifier_verified BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS identifier_verified_at TIMESTAMPTZ,
    -- For password-reset throttling.
    ADD COLUMN IF NOT EXISTS password_set_at     TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS failed_login_count  INT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS locked_until        TIMESTAMPTZ;

-- Existing pre-Slice-35 staff are already "verified" because they signed up via the
-- old OTP-only flow which implicitly verified them at first login. Mark them so they
-- aren't forced through verification again.
UPDATE staff SET identifier_verified = TRUE, identifier_verified_at = COALESCE(created_at, NOW())
WHERE identifier_verified = FALSE;
