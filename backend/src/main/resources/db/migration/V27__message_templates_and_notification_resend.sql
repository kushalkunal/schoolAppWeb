-- V27: configurable per-tenant message templates + notification_log resend support
-- Allows schools to override the default WhatsApp/SMS message bodies for common events.

CREATE TABLE message_templates (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id   UUID        NOT NULL REFERENCES schools(id) ON DELETE CASCADE,
    template_key VARCHAR(60) NOT NULL,  -- matches MessageType enum name
    body_template TEXT       NOT NULL,
    updated_by  UUID         REFERENCES staff(id),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_message_templates_school_key UNIQUE (school_id, template_key)
);

CREATE INDEX idx_message_templates_school ON message_templates(school_id);

-- Extend notification_log with resend support
ALTER TABLE notification_log
    ADD COLUMN IF NOT EXISTS retry_count   INT         NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS retried_at    TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS parent_log_id UUID        REFERENCES notification_log(id);

-- Add index for paginated log queries (list by school ordered by recency)
CREATE INDEX IF NOT EXISTS idx_notification_log_school_created
    ON notification_log(school_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_notification_log_school_status
    ON notification_log(school_id, status, created_at DESC);
