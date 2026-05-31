-- Delivery-flow E2E prerequisites for live-workflows.spec.ts (Flows 2 & 3).
--
-- The base VMS seed ships no parents, so circular dispatch and absence alerts have no
-- recipient to write a notification_log row for. This idempotent seed adds one parent with a
-- phone, links them as the primary guardian of student d0000001 (section 1A), and enables the
-- parent-notification feature flags for the tenant.
--
-- Run against the schoolapp DB (compose maps it to localhost:55432):
--   docker exec -i schoolapp-postgres psql -U schoolapp -d schoolapp < web/e2e/seed/delivery-prereq.sql
-- Restart the backend afterwards so the feature-flag cache picks up the new overrides.

SET app.current_tenant = '926c372c-139d-460d-83b1-1a80ef92db57';

INSERT INTO parents (id, school_id, name, phone, email, relation_type, created_at)
VALUES ('aaaa0001-0000-0000-0000-000000000001', '926c372c-139d-460d-83b1-1a80ef92db57',
        'E2E Parent', '+919900000001', 'e2e.parent@vms.school', 'FATHER', now())
ON CONFLICT (id) DO UPDATE SET phone = EXCLUDED.phone;

INSERT INTO student_parent_links (id, student_id, parent_id, relation, is_primary, created_at)
VALUES ('aaaa0002-0000-0000-0000-000000000001', 'd0000001-0000-0000-0000-000000000001',
        'aaaa0001-0000-0000-0000-000000000001', 'FATHER', true, now())
ON CONFLICT (id) DO NOTHING;

INSERT INTO feature_overrides (id, school_id, feature_key, enabled, config, created_at, updated_at)
VALUES ('aaaa0003-0000-0000-0000-000000000001', '926c372c-139d-460d-83b1-1a80ef92db57',
        'PARENT_NOTIFICATIONS', true, '{}', now(), now()),
       ('aaaa0004-0000-0000-0000-000000000001', '926c372c-139d-460d-83b1-1a80ef92db57',
        'PARENT_NOTIFY_ABSENCE', true, '{}', now(), now())
ON CONFLICT (id) DO UPDATE SET enabled = true;
