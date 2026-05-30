-- V41 — Visitor student-pickup authorization (audit fix #16, child safety).
-- A student-pickup visit must be by a registered guardian (parent phone linked to the student);
-- otherwise the gate user must record an explicit override reason.

ALTER TABLE visitors
    ADD COLUMN IF NOT EXISTS student_pickup         BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS pickup_authorized      BOOLEAN,   -- NULL = not a pickup; TRUE/FALSE for pickups
    ADD COLUMN IF NOT EXISTS pickup_override_reason TEXT;
