-- Classrooms / rooms and their use in the timetable, enabling room double-booking detection.

CREATE TABLE classrooms (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id  UUID NOT NULL REFERENCES schools (id) ON DELETE CASCADE,
    name       VARCHAR(80)  NOT NULL,
    code       VARCHAR(30),
    building   VARCHAR(80),
    capacity   INT,
    room_type  VARCHAR(20) NOT NULL DEFAULT 'CLASSROOM',  -- CLASSROOM | LAB | LIBRARY | HALL | OTHER
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_classroom_name UNIQUE (school_id, name)
);
CREATE INDEX idx_classrooms_school ON classrooms (school_id);

-- A timetable slot may be held in a room. Nullable: not every period needs a fixed room.
ALTER TABLE timetable_entries ADD COLUMN room_id UUID REFERENCES classrooms (id) ON DELETE SET NULL;
CREATE INDEX idx_timetable_entries_room ON timetable_entries (room_id);

-- Tenant isolation for the new table (post-V37 tables must opt in explicitly).
ALTER TABLE classrooms ENABLE ROW LEVEL SECURITY;
ALTER TABLE classrooms FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_isolation ON classrooms;
CREATE POLICY tenant_isolation ON classrooms
    USING (
        NULLIF(current_setting('app.current_tenant', true), '') IS NULL
        OR school_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid
    )
    WITH CHECK (
        NULLIF(current_setting('app.current_tenant', true), '') IS NULL
        OR school_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid
    );
