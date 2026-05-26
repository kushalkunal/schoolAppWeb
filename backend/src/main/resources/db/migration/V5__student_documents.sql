-- ============================================================
-- V5: Student documents (Slice 3 continuation / gap analysis §5.1)
-- Uploaded paperwork (admission form, birth cert, TC, photo, medical, other) kept per
-- student. Storage backend is the same FileStorageService abstraction used for receipts —
-- this table just records the key + metadata so documents can be listed/downloaded.
-- ============================================================

CREATE TABLE student_documents (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id       UUID NOT NULL REFERENCES schools(id),
    student_id      UUID NOT NULL REFERENCES students(id),
    doc_type        VARCHAR(30) NOT NULL,
    storage_key     TEXT NOT NULL,
    file_url        TEXT NOT NULL,
    file_name       VARCHAR(255),
    content_type    VARCHAR(100),
    size_bytes      BIGINT,
    uploaded_by_id  UUID REFERENCES staff(id),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_student_documents_student
    ON student_documents(student_id, created_at DESC);
CREATE INDEX idx_student_documents_school
    ON student_documents(school_id, created_at DESC);
