-- V31: Admit card (hall ticket) generation with fee-clearance gate
-- status: BLOCKED = unpaid fees, PENDING = eligible but not yet generated,
--         GENERATED = PDF stored, DOWNLOADED = downloaded at least once

CREATE TABLE admit_cards (
    id                          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id                   UUID        NOT NULL REFERENCES schools(id),
    exam_id                     UUID        NOT NULL REFERENCES exams(id) ON DELETE CASCADE,
    student_id                  UUID        NOT NULL REFERENCES students(id),
    status                      TEXT        NOT NULL DEFAULT 'PENDING',
    -- null while BLOCKED; populated once PDF is generated
    pdf_url                     TEXT,
    admit_card_no               TEXT,
    seat_number                 TEXT,
    -- snapshot of outstanding paise at generation time (0 = cleared)
    outstanding_paise_snapshot  BIGINT      NOT NULL DEFAULT 0,
    fee_cleared                 BOOLEAN     NOT NULL DEFAULT FALSE,
    generated_at                TIMESTAMPTZ,
    -- last time a notification was sent about this card being blocked
    last_notified_at            TIMESTAMPTZ,
    created_at                  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at                  TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE(exam_id, student_id)
);

CREATE INDEX idx_admit_cards_exam    ON admit_cards(exam_id);
CREATE INDEX idx_admit_cards_student ON admit_cards(student_id);
CREATE INDEX idx_admit_cards_school  ON admit_cards(school_id);
CREATE INDEX idx_admit_cards_status  ON admit_cards(school_id, status);

-- Per-exam settings for auto-generation and fee-clearance gate
CREATE TABLE admit_card_settings (
    id                      UUID    PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id               UUID    NOT NULL REFERENCES schools(id),
    exam_id                 UUID    NOT NULL REFERENCES exams(id) ON DELETE CASCADE,
    days_before             INT     NOT NULL DEFAULT 5,
    fee_clearance_required  BOOLEAN NOT NULL DEFAULT TRUE,
    notify_blocked          BOOLEAN NOT NULL DEFAULT TRUE,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE(school_id, exam_id)
);
