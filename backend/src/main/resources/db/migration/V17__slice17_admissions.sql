-- =====================================================================================
-- Slice 17 — Admissions funnel (ENQUIRY → APPLICATION → TEST → OFFER → ENROLLED)
-- =====================================================================================
-- Single mutable table walks the lifecycle. We capture every transition timestamp instead
-- of a separate state-history table — it keeps lookups O(1) for "where is enquiry X stuck?"
-- analytics, and is sufficient for the linear flow.
--
-- A successful enrolment links to an existing Student row via enrolled_student_id; the
-- service creates the Student inside the same transaction so the link is never dangling.
-- =====================================================================================

CREATE TABLE admissions (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id             UUID NOT NULL REFERENCES schools(id) ON DELETE CASCADE,
    -- Lifecycle status. Values:
    --   ENQUIRY                 - public enquiry just landed
    --   APPLICATION_SUBMITTED   - applicant filled the full application
    --   TEST_SCHEDULED          - entrance test scheduled
    --   TEST_COMPLETED          - test scored, decision pending
    --   OFFERED                 - offer letter issued
    --   ACCEPTED                - parent accepted
    --   DECLINED                - parent declined
    --   ENROLLED                - student row created
    --   WITHDRAWN               - dropped out post-offer (uncommon but real)
    --   REJECTED                - admin rejected (test score / interview)
    status                VARCHAR(30) NOT NULL DEFAULT 'ENQUIRY',
    -- ---------- enquirer details ----------
    parent_name           VARCHAR(200),
    parent_phone          VARCHAR(20) NOT NULL,
    parent_email          VARCHAR(255),
    -- ---------- applicant details ----------
    student_first_name    VARCHAR(100) NOT NULL,
    student_last_name     VARCHAR(100),
    student_date_of_birth DATE,
    student_gender        VARCHAR(10),
    -- ---------- intended placement ----------
    intended_class        VARCHAR(50) NOT NULL,         -- "Class 5"
    intended_section      VARCHAR(20),                  -- optional preference
    intended_academic_year VARCHAR(20),                 -- e.g. "2026-2027"
    -- ---------- attribution ----------
    source                VARCHAR(40),                  -- WALK_IN | WEBSITE | REFERRAL | AD | OTHER
    referrer_name         VARCHAR(200),
    notes                 TEXT,
    -- ---------- entrance test ----------
    test_scheduled_at     TIMESTAMPTZ,
    test_venue            TEXT,
    test_total_marks      INT,
    test_obtained_marks   INT,
    test_remarks          TEXT,
    -- ---------- offer ----------
    offer_letter_url      TEXT,                          -- PDF generated via DocumentService
    offer_issued_at       TIMESTAMPTZ,
    offer_accepted_at     TIMESTAMPTZ,
    offer_declined_at     TIMESTAMPTZ,
    decline_reason        TEXT,
    -- ---------- enrolment outcome ----------
    enrolled_student_id   UUID REFERENCES students(id) ON DELETE SET NULL,
    enrolled_at           TIMESTAMPTZ,
    -- ---------- audit ----------
    assigned_to_id        UUID,                          -- staff handling this funnel item
    created_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by_id         UUID
);
CREATE INDEX idx_admissions_school        ON admissions(school_id);
CREATE INDEX idx_admissions_status        ON admissions(school_id, status);
CREATE INDEX idx_admissions_phone         ON admissions(parent_phone);
CREATE INDEX idx_admissions_created       ON admissions(school_id, created_at DESC);


-- Per-subject test scores. Optional — schools that just record a single total skip this.
CREATE TABLE admission_test_scores (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    admission_id    UUID NOT NULL REFERENCES admissions(id) ON DELETE CASCADE,
    school_id       UUID NOT NULL REFERENCES schools(id) ON DELETE CASCADE,
    subject_name    VARCHAR(100) NOT NULL,
    max_marks       INT NOT NULL,
    obtained_marks  INT NOT NULL,
    remarks         TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE(admission_id, subject_name)
);
CREATE INDEX idx_admission_test_scores_admission ON admission_test_scores(admission_id);
