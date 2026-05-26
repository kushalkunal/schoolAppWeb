-- =====================================================================================
-- Slice 18 — Hostel, Cafeteria, Inventory
-- =====================================================================================

-- =====================================================================================
-- 1. HOSTEL
-- =====================================================================================
CREATE TABLE hostels (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id     UUID NOT NULL REFERENCES schools(id) ON DELETE CASCADE,
    name          VARCHAR(120) NOT NULL,
    gender        VARCHAR(10),                 -- BOYS | GIRLS | COED | null
    address       TEXT,
    warden_staff_id UUID,                      -- REFERENCES staff(id) — soft (cross-module)
    total_rooms   INT NOT NULL DEFAULT 0,      -- denormalised, maintained by the service
    capacity      INT NOT NULL DEFAULT 0,
    active        BOOLEAN NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE(school_id, name)
);

CREATE TABLE hostel_rooms (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id       UUID NOT NULL REFERENCES schools(id) ON DELETE CASCADE,
    hostel_id       UUID NOT NULL REFERENCES hostels(id) ON DELETE CASCADE,
    room_number     VARCHAR(20) NOT NULL,
    floor           INT,
    -- SINGLE | DOUBLE | TRIPLE | DORM
    room_type       VARCHAR(15) NOT NULL,
    capacity        INT NOT NULL,
    current_occupancy INT NOT NULL DEFAULT 0,
    notes           TEXT,
    active          BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE(hostel_id, room_number)
);
CREATE INDEX idx_rooms_school ON hostel_rooms(school_id);

CREATE TABLE hostel_allocations (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id         UUID NOT NULL REFERENCES schools(id) ON DELETE CASCADE,
    room_id           UUID NOT NULL REFERENCES hostel_rooms(id) ON DELETE CASCADE,
    student_id        UUID NOT NULL REFERENCES students(id) ON DELETE CASCADE,
    allocated_from    DATE NOT NULL,
    allocated_until   DATE,                       -- NULL = open-ended (vacating fills this)
    status            VARCHAR(10) NOT NULL DEFAULT 'ACTIVE',      -- ACTIVE | VACATED
    vacated_at        TIMESTAMPTZ,
    vacated_reason    TEXT,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by_id     UUID
);
CREATE INDEX idx_alloc_room    ON hostel_allocations(room_id) WHERE status = 'ACTIVE';
CREATE INDEX idx_alloc_student ON hostel_allocations(student_id) WHERE status = 'ACTIVE';
CREATE INDEX idx_alloc_school  ON hostel_allocations(school_id);

CREATE TABLE hostel_visitor_logs (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id       UUID NOT NULL REFERENCES schools(id) ON DELETE CASCADE,
    hostel_id       UUID NOT NULL REFERENCES hostels(id) ON DELETE CASCADE,
    visiting_student_id UUID REFERENCES students(id) ON DELETE SET NULL,
    visitor_name    VARCHAR(200) NOT NULL,
    visitor_phone   VARCHAR(20),
    relation        VARCHAR(50),
    id_proof        VARCHAR(100),
    purpose         TEXT,
    in_time         TIMESTAMPTZ NOT NULL,
    out_time        TIMESTAMPTZ,                       -- NULL = visitor still inside
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by_id   UUID
);
CREATE INDEX idx_visitor_school_in ON hostel_visitor_logs(school_id, in_time DESC);
CREATE INDEX idx_visitor_active    ON hostel_visitor_logs(school_id) WHERE out_time IS NULL;


-- =====================================================================================
-- 2. CAFETERIA
-- =====================================================================================
CREATE TABLE cafeteria_menu_items (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id       UUID NOT NULL REFERENCES schools(id) ON DELETE CASCADE,
    name            VARCHAR(120) NOT NULL,
    -- BREAKFAST | LUNCH | SNACK | DINNER | BEVERAGE | OTHER
    category        VARCHAR(20),
    description     TEXT,
    price_paise     BIGINT NOT NULL,
    image_url       TEXT,
    available       BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_menu_school   ON cafeteria_menu_items(school_id);

CREATE TABLE cafeteria_wallets (
    id                       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id                UUID NOT NULL REFERENCES schools(id) ON DELETE CASCADE,
    student_id               UUID NOT NULL REFERENCES students(id) ON DELETE CASCADE,
    balance_paise            BIGINT NOT NULL DEFAULT 0,
    total_topped_up_paise    BIGINT NOT NULL DEFAULT 0,
    total_spent_paise        BIGINT NOT NULL DEFAULT 0,
    last_topped_up_at        TIMESTAMPTZ,
    created_at               TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at               TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE(school_id, student_id)
);

CREATE TABLE cafeteria_orders (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id         UUID NOT NULL REFERENCES schools(id) ON DELETE CASCADE,
    student_id        UUID NOT NULL REFERENCES students(id) ON DELETE CASCADE,
    wallet_id         UUID REFERENCES cafeteria_wallets(id) ON DELETE SET NULL,
    total_paise       BIGINT NOT NULL,
    -- PLACED | FULFILLED | CANCELLED | REFUNDED
    status            VARCHAR(15) NOT NULL DEFAULT 'PLACED',
    placed_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    fulfilled_at      TIMESTAMPTZ,
    cancelled_at      TIMESTAMPTZ,
    cancel_reason     TEXT,
    notes             TEXT,
    created_by_id     UUID
);
CREATE INDEX idx_orders_student ON cafeteria_orders(student_id, placed_at DESC);
CREATE INDEX idx_orders_school  ON cafeteria_orders(school_id, placed_at DESC);

CREATE TABLE cafeteria_order_items (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id        UUID NOT NULL REFERENCES cafeteria_orders(id) ON DELETE CASCADE,
    menu_item_id    UUID NOT NULL REFERENCES cafeteria_menu_items(id),
    quantity        INT NOT NULL,
    unit_price_paise BIGINT NOT NULL,                  -- snapshotted at order time
    line_total_paise BIGINT NOT NULL
);
CREATE INDEX idx_order_items_order ON cafeteria_order_items(order_id);

CREATE TABLE cafeteria_wallet_transactions (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id       UUID NOT NULL REFERENCES schools(id) ON DELETE CASCADE,
    wallet_id       UUID NOT NULL REFERENCES cafeteria_wallets(id) ON DELETE CASCADE,
    -- TOPUP | DEBIT | REFUND
    txn_type        VARCHAR(10) NOT NULL,
    amount_paise    BIGINT NOT NULL,
    ref_order_id    UUID REFERENCES cafeteria_orders(id) ON DELETE SET NULL,
    balance_after_paise BIGINT NOT NULL,
    notes           TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by_id   UUID
);
CREATE INDEX idx_wallet_txn_wallet ON cafeteria_wallet_transactions(wallet_id, created_at DESC);


-- =====================================================================================
-- 3. INVENTORY
-- =====================================================================================
CREATE TABLE inventory_categories (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id     UUID NOT NULL REFERENCES schools(id) ON DELETE CASCADE,
    name          VARCHAR(120) NOT NULL,
    description   TEXT,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE(school_id, name)
);

CREATE TABLE inventory_items (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id         UUID NOT NULL REFERENCES schools(id) ON DELETE CASCADE,
    category_id       UUID REFERENCES inventory_categories(id) ON DELETE SET NULL,
    asset_tag         VARCHAR(60),                -- school-issued barcode / sticker
    name              VARCHAR(200) NOT NULL,
    description       TEXT,
    serial_number     VARCHAR(120),
    quantity          INT NOT NULL DEFAULT 1,
    unit_cost_paise   BIGINT,
    purchase_date     DATE,
    purchase_invoice_url TEXT,
    -- AVAILABLE | ISSUED | UNDER_MAINTENANCE | LOST | RETIRED
    status            VARCHAR(20) NOT NULL DEFAULT 'AVAILABLE',
    location          TEXT,                        -- "Lab 1", "Library cabinet 3"
    notes             TEXT,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE(school_id, asset_tag)
);
CREATE INDEX idx_inv_items_school   ON inventory_items(school_id);
CREATE INDEX idx_inv_items_status   ON inventory_items(school_id, status);
CREATE INDEX idx_inv_items_category ON inventory_items(category_id);

CREATE TABLE inventory_issuances (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id       UUID NOT NULL REFERENCES schools(id) ON DELETE CASCADE,
    item_id         UUID NOT NULL REFERENCES inventory_items(id) ON DELETE CASCADE,
    -- One of these is set — issued to a staff or a student.
    issued_to_staff_id   UUID,                    -- soft cross-module ref
    issued_to_student_id UUID,                    -- soft cross-module ref
    issued_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    issued_by_id    UUID,
    expected_return_at TIMESTAMPTZ,
    returned_at     TIMESTAMPTZ,
    return_condition VARCHAR(20),                 -- GOOD | DAMAGED | LOST
    notes           TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT issuance_target_xor CHECK (
        (issued_to_staff_id IS NOT NULL)::int + (issued_to_student_id IS NOT NULL)::int = 1
    )
);
CREATE INDEX idx_inv_issuance_item    ON inventory_issuances(item_id);
CREATE INDEX idx_inv_issuance_active  ON inventory_issuances(school_id) WHERE returned_at IS NULL;

CREATE TABLE inventory_maintenance (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id       UUID NOT NULL REFERENCES schools(id) ON DELETE CASCADE,
    item_id         UUID NOT NULL REFERENCES inventory_items(id) ON DELETE CASCADE,
    performed_at    TIMESTAMPTZ NOT NULL,
    cost_paise      BIGINT,
    description     TEXT NOT NULL,
    performed_by    VARCHAR(200),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by_id   UUID
);
CREATE INDEX idx_inv_maint_item ON inventory_maintenance(item_id, performed_at DESC);
