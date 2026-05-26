-- =====================================================================================
-- V11 — Library module
-- =====================================================================================

CREATE TABLE library_books (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id       UUID NOT NULL REFERENCES schools(id) ON DELETE CASCADE,
    title           VARCHAR(255) NOT NULL,
    author          VARCHAR(255),
    isbn            VARCHAR(20),
    publisher       VARCHAR(255),
    category        VARCHAR(80),
    -- Total physical copies; available_copies decremented on issue, incremented on return.
    total_copies    INTEGER NOT NULL DEFAULT 1,
    available_copies INTEGER NOT NULL DEFAULT 1,
    is_active       BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by_id   UUID,
    UNIQUE(school_id, isbn)
);
CREATE INDEX idx_library_books_school ON library_books(school_id, is_active);
CREATE INDEX idx_library_books_title_trgm ON library_books USING GIN (title gin_trgm_ops);

CREATE TABLE library_issues (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id       UUID NOT NULL REFERENCES schools(id) ON DELETE CASCADE,
    book_id         UUID NOT NULL REFERENCES library_books(id),
    student_id      UUID NOT NULL REFERENCES students(id),
    issued_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    due_date        DATE NOT NULL,
    returned_at     TIMESTAMPTZ,
    fine_paise      BIGINT NOT NULL DEFAULT 0,         -- per-day overdue fine, computed on return
    issued_by_id    UUID,
    note            TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by_id   UUID
);
CREATE INDEX idx_library_issues_student ON library_issues(student_id, issued_at DESC);
CREATE INDEX idx_library_issues_outstanding ON library_issues(school_id, returned_at)
    WHERE returned_at IS NULL;
