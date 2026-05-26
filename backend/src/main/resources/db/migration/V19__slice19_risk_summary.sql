-- =====================================================================================
-- Slice 19 — Plain-English narrative on student_risk_scores
-- =====================================================================================
-- Adds a TEXT column holding a 2-3 sentence LLM-written description of why this student
-- is flagged. Filled by AtRiskDetectionService when an LLM provider is configured;
-- otherwise the service falls back to a templated string so the column is never empty
-- on freshly-scored rows.
-- =====================================================================================

ALTER TABLE student_risk_scores
    ADD COLUMN summary TEXT;
