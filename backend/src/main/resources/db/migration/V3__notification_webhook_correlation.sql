-- ============================================================
-- V3: Webhook correlation columns
-- Slice 6.5: fast lookup of inbound messages by parent phone + resolving a delivery
--           status update from a BSP requires an index on wa_message_id across tenants.
-- Slice 7.5: payment webhook → FeePayment auto-create needs an idempotency guarantee.
--           A second webhook delivery for the same provider_reference must be a no-op
--           rather than a duplicate row (BSPs retry aggressively on 5xx).
-- ============================================================

-- Slice 7.5: record of the upstream provider payment reference (Stripe session id /
-- Razorpay payment_link id). Unique when present — the webhook idempotency key.
ALTER TABLE fee_payments
    ADD COLUMN IF NOT EXISTS provider_reference VARCHAR(100);

CREATE UNIQUE INDEX IF NOT EXISTS uq_fee_payments_provider_reference
    ON fee_payments(provider_reference)
    WHERE provider_reference IS NOT NULL;

-- Slice 6.5: cross-tenant wa_message_id lookup already exists in V1 as a partial index —
-- no change needed. Here we just add a targeted index on recipient_phone so the 2-way
-- inbox can find "the last message we sent this phone" quickly for threading.
CREATE INDEX IF NOT EXISTS idx_notif_recipient_phone
    ON notification_log(recipient_phone, created_at DESC);
