-- =====================================================================================
-- V12 — Transport module
-- =====================================================================================

CREATE TABLE transport_routes (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id       UUID NOT NULL REFERENCES schools(id) ON DELETE CASCADE,
    name            VARCHAR(120) NOT NULL,        -- "Route A — North Pune"
    -- Stops as ordered JSONB: [{"order":1,"name":"Aundh","time":"07:30"}, ...]
    stops           JSONB NOT NULL DEFAULT '[]'::jsonb,
    fare_paise      BIGINT NOT NULL DEFAULT 0,    -- monthly fare for the route
    is_active       BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by_id   UUID,
    UNIQUE(school_id, name)
);
CREATE INDEX idx_transport_routes_school ON transport_routes(school_id, is_active);

CREATE TABLE transport_vehicles (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id           UUID NOT NULL REFERENCES schools(id) ON DELETE CASCADE,
    registration_no     VARCHAR(40) NOT NULL,        -- "MH 12 AB 1234"
    driver_staff_id     UUID REFERENCES staff(id),
    capacity            INTEGER NOT NULL DEFAULT 30,
    route_id            UUID REFERENCES transport_routes(id),
    is_active           BOOLEAN NOT NULL DEFAULT TRUE,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by_id       UUID,
    UNIQUE(school_id, registration_no)
);
CREATE INDEX idx_transport_vehicles_school ON transport_vehicles(school_id, is_active);
CREATE INDEX idx_transport_vehicles_route ON transport_vehicles(route_id);

CREATE TABLE student_transport_assignments (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id       UUID NOT NULL REFERENCES schools(id) ON DELETE CASCADE,
    student_id      UUID NOT NULL REFERENCES students(id) ON DELETE CASCADE,
    route_id        UUID NOT NULL REFERENCES transport_routes(id),
    stop_name       VARCHAR(120),                  -- which stop on the route
    start_date      DATE NOT NULL,
    end_date        DATE,                           -- null = ongoing
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by_id   UUID,
    -- One ongoing assignment per student (enforced by app, not DDL — students may have a
    -- historic ended assignment alongside a new active one).
    UNIQUE(student_id, start_date)
);
CREATE INDEX idx_sta_student ON student_transport_assignments(student_id, end_date);
CREATE INDEX idx_sta_route ON student_transport_assignments(route_id, end_date);
