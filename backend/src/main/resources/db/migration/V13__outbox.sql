-- =====================================================================================
-- V13 — Transactional outbox for durable async events
--
-- Pattern: when a business write needs to publish an event, insert an outbox_events row in
-- the SAME transaction. A poller (Spring @Scheduled, every 5s) reads unprocessed rows and
-- republishes them via Spring ApplicationEventPublisher — the existing async listeners
-- (AbsenceAlertService, ReceiptDeliveryListener, ReportCardDeliveryListener, etc.) keep
-- receiving them unchanged.
--
-- Why: today's @Async listeners survive a JVM restart only if the listener completed
-- before the restart. An attendance submission followed by an immediate crash loses the
-- queued WhatsApp absence alerts. With the outbox they're replayed on restart.
-- =====================================================================================

CREATE TABLE outbox_events (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    -- school_id is nullable for platform-level events (e.g. cross-tenant billing webhook
    -- transitions). For tenant-scoped events, set it for traceability + filtered queries.
    school_id        UUID,
    topic            VARCHAR(120) NOT NULL,    -- fully-qualified event class name, e.g.
                                                -- "in.schoolapp.attendance.event.AttendanceSubmittedEvent"
    payload          JSONB NOT NULL,            -- the event object as JSON
    attempts         INTEGER NOT NULL DEFAULT 0,
    last_error       TEXT,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    -- Next time the poller is allowed to pick up this row. Set to created_at on insert;
    -- the poller updates it to NOW() + backoff on failure for exponential retry.
    next_attempt_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    -- Stamped once the event has been republished successfully.
    processed_at     TIMESTAMPTZ
);

-- Polling index — only rows still pending. WHERE-clause keeps the index tiny.
CREATE INDEX idx_outbox_pending ON outbox_events(next_attempt_at)
    WHERE processed_at IS NULL;

CREATE INDEX idx_outbox_school_created ON outbox_events(school_id, created_at DESC);
