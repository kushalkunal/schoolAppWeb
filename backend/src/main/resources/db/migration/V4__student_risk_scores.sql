-- ============================================================
-- V4: At-risk student scoring (Slice 10b)
-- One row per student, upserted weekly by AtRiskDetectionService. The composite score
-- blends attendance, fee, and exam trend into a single 0-100 number the dashboard can sort by.
-- Historical tracking is deferred — for now "latest state" is what dashboards need. When we
-- need month-over-month charts we'll add a student_risk_scores_history table alongside.
-- ============================================================

CREATE TABLE student_risk_scores (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id               UUID NOT NULL REFERENCES schools(id),
    student_id              UUID NOT NULL REFERENCES students(id),
    -- Composite 0–100; higher = more at risk.
    score                   INTEGER NOT NULL,
    attendance_pct          NUMERIC(5,2),
    fee_outstanding_paise   BIGINT NOT NULL DEFAULT 0,
    marks_trend             VARCHAR(10),  -- UP | DOWN | FLAT | UNKNOWN
    top_factor              VARCHAR(40),  -- ATTENDANCE | FEE | MARKS
    calculated_at           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE(school_id, student_id)
);

CREATE INDEX idx_risk_scores_school_score
    ON student_risk_scores(school_id, score DESC);
