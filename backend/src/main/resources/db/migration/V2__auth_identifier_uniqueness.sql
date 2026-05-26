-- ============================================================
-- V2: Auth identifier uniqueness + email-based signup support
-- Phone and email are both login identifiers — either can authenticate a staff member — and
-- each must be globally unique across all tenants.
-- Phone is made nullable so an EMAIL-only signup can land without a phone number.
-- ============================================================

-- Make phone nullable to allow email-only signup (feature flag: app.signup.channel=EMAIL)
ALTER TABLE schools ALTER COLUMN phone DROP NOT NULL;
ALTER TABLE staff   ALTER COLUMN phone DROP NOT NULL;

-- Globally unique phone for staff (login identifier; partial on NOT NULL for email-only staff)
CREATE UNIQUE INDEX uq_staff_phone_global ON staff(phone) WHERE phone IS NOT NULL;

-- Emails are globally unique when present (login identifier)
CREATE UNIQUE INDEX uq_staff_email   ON staff(email)   WHERE email IS NOT NULL;
CREATE UNIQUE INDEX uq_schools_email ON schools(email) WHERE email IS NOT NULL;
