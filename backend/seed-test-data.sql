-- ============================================================
-- Seed data for E2E tests (day-to-day-operations.spec.ts)
-- Run against a fresh DB after all migrations have been applied.
-- ============================================================

-- ============================================================
-- SCHOOL / TENANT
-- ============================================================
INSERT INTO schools (id, name, principal_name, email, city, state, board, is_active, created_at, updated_at)
VALUES (
    '926c372c-139d-460d-83b1-1a80ef92db57',
    'VMS School',
    'Rajesh Kumar',
    'admin@vms.school',
    'Bengaluru',
    'Karnataka',
    'CBSE',
    TRUE,
    NOW(),
    NOW()
);

-- ============================================================
-- ACADEMIC YEAR
-- ============================================================
INSERT INTO academic_years (id, school_id, name, start_date, end_date, is_current, created_at)
VALUES (
    '8fd977b1-e495-42a5-8ef9-473aabd57ff5',
    '926c372c-139d-460d-83b1-1a80ef92db57',
    '2025-26',
    '2025-04-01',
    '2026-03-31',
    TRUE,
    NOW()
);

-- ============================================================
-- SUBSCRIPTION (GROWTH plan — includes SUBSTITUTE_TEACHERS)
-- ============================================================
INSERT INTO subscriptions (id, school_id, plan_id, status, current_period_start, current_period_end, created_at, updated_at)
VALUES (
    gen_random_uuid(),
    '926c372c-139d-460d-83b1-1a80ef92db57',
    (SELECT id FROM plans WHERE code = 'GROWTH'),
    'ACTIVE',
    NOW(),
    NOW() + INTERVAL '1 year',
    NOW(),
    NOW()
);

-- ============================================================
-- CLASSES (1 through 6)
-- ============================================================
INSERT INTO school_classes (id, school_id, name, sort_order, created_at) VALUES
    ('a1000001-0000-0000-0000-000000000001', '926c372c-139d-460d-83b1-1a80ef92db57', 'Class 1', 1, NOW()),
    ('a1000002-0000-0000-0000-000000000002', '926c372c-139d-460d-83b1-1a80ef92db57', 'Class 2', 2, NOW()),
    ('a1000003-0000-0000-0000-000000000003', '926c372c-139d-460d-83b1-1a80ef92db57', 'Class 3', 3, NOW()),
    ('a1000004-0000-0000-0000-000000000004', '926c372c-139d-460d-83b1-1a80ef92db57', 'Class 4', 4, NOW()),
    ('a1000005-0000-0000-0000-000000000005', '926c372c-139d-460d-83b1-1a80ef92db57', 'Class 5', 5, NOW()),
    ('a1000006-0000-0000-0000-000000000006', '926c372c-139d-460d-83b1-1a80ef92db57', 'Class 6', 6, NOW());

-- ============================================================
-- STAFF (principal + 8 class teachers)
-- password_hash values:
--   Test@1234       -> $2b$10$pNprnL.g4fINpdsv9O1UxOD8Ja9JpVRBYLHIjLSHg/wF0TIyudfo.
--   Teacher@123     -> $2b$10$8XFYtBztve0ro4hOvJHa/O/n0CdIPHjjdXQrT2ViwDLFT2G1qCHES
--   Priya@School123 -> $2b$10$hkpegKbjSYdChGT2a9O52.s4UAWwry/9TbigHc.MwNsbWSWfsauEC
-- ============================================================
INSERT INTO staff (id, school_id, first_name, last_name, email, role, is_active,
                   password_hash, identifier_verified, must_reset_password,
                   failed_login_count, created_at, updated_at)
VALUES
    -- Principal
    (
        'abf43558-31dd-478e-a382-1ecf3f0aedc7',
        '926c372c-139d-460d-83b1-1a80ef92db57',
        'Rajesh', 'Kumar', 'teacher@vms.school', 'PRINCIPAL', TRUE,
        '$2b$10$pNprnL.g4fINpdsv9O1UxOD8Ja9JpVRBYLHIjLSHg/wF0TIyudfo.',
        TRUE, FALSE, 0, NOW(), NOW()
    ),
    -- Ananya Singh (Class 1A teacher)
    (
        'e38bd555-29e6-4ab1-a725-f4d2687806ee',
        '926c372c-139d-460d-83b1-1a80ef92db57',
        'Ananya', 'Singh', 'ananya.singh@vms.school', 'CLASS_TEACHER', TRUE,
        '$2b$10$8XFYtBztve0ro4hOvJHa/O/n0CdIPHjjdXQrT2ViwDLFT2G1qCHES',
        TRUE, FALSE, 0, NOW(), NOW()
    ),
    -- Rohan Mehta (Class 1B teacher)
    (
        'b0000001-0000-0000-0000-000000000001',
        '926c372c-139d-460d-83b1-1a80ef92db57',
        'Rohan', 'Mehta', 'rohan.mehta@vms.school', 'CLASS_TEACHER', TRUE,
        '$2b$10$8XFYtBztve0ro4hOvJHa/O/n0CdIPHjjdXQrT2ViwDLFT2G1qCHES',
        TRUE, FALSE, 0, NOW(), NOW()
    ),
    -- Deepa Nair (Class 2A teacher)
    (
        'b0000002-0000-0000-0000-000000000002',
        '926c372c-139d-460d-83b1-1a80ef92db57',
        'Deepa', 'Nair', 'deepa.nair@vms.school', 'CLASS_TEACHER', TRUE,
        '$2b$10$8XFYtBztve0ro4hOvJHa/O/n0CdIPHjjdXQrT2ViwDLFT2G1qCHES',
        TRUE, FALSE, 0, NOW(), NOW()
    ),
    -- Suresh Patel (Class 2B teacher)
    (
        'b0000003-0000-0000-0000-000000000003',
        '926c372c-139d-460d-83b1-1a80ef92db57',
        'Suresh', 'Patel', 'suresh.patel@vms.school', 'CLASS_TEACHER', TRUE,
        '$2b$10$8XFYtBztve0ro4hOvJHa/O/n0CdIPHjjdXQrT2ViwDLFT2G1qCHES',
        TRUE, FALSE, 0, NOW(), NOW()
    ),
    -- Meera Iyer (Class 3A teacher)
    (
        'b0000004-0000-0000-0000-000000000004',
        '926c372c-139d-460d-83b1-1a80ef92db57',
        'Meera', 'Iyer', 'meera.iyer@vms.school', 'CLASS_TEACHER', TRUE,
        '$2b$10$8XFYtBztve0ro4hOvJHa/O/n0CdIPHjjdXQrT2ViwDLFT2G1qCHES',
        TRUE, FALSE, 0, NOW(), NOW()
    ),
    -- Arjun Kumar (Class 3B teacher)
    (
        'b0000005-0000-0000-0000-000000000005',
        '926c372c-139d-460d-83b1-1a80ef92db57',
        'Arjun', 'Kumar', 'arjun.kumar@vms.school', 'CLASS_TEACHER', TRUE,
        '$2b$10$8XFYtBztve0ro4hOvJHa/O/n0CdIPHjjdXQrT2ViwDLFT2G1qCHES',
        TRUE, FALSE, 0, NOW(), NOW()
    ),
    -- Lakshmi Devi (Class 4A teacher)
    (
        'b0000006-0000-0000-0000-000000000006',
        '926c372c-139d-460d-83b1-1a80ef92db57',
        'Lakshmi', 'Devi', 'lakshmi.devi@vms.school', 'CLASS_TEACHER', TRUE,
        '$2b$10$8XFYtBztve0ro4hOvJHa/O/n0CdIPHjjdXQrT2ViwDLFT2G1qCHES',
        TRUE, FALSE, 0, NOW(), NOW()
    ),
    -- Priya Sharma (Class 4B teacher, different password)
    (
        'b0000007-0000-0000-0000-000000000007',
        '926c372c-139d-460d-83b1-1a80ef92db57',
        'Priya', 'Sharma', 'priya.sharma@vms.school', 'CLASS_TEACHER', TRUE,
        '$2b$10$hkpegKbjSYdChGT2a9O52.s4UAWwry/9TbigHc.MwNsbWSWfsauEC',
        TRUE, FALSE, 0, NOW(), NOW()
    );

-- ============================================================
-- SECTIONS (A and B for Classes 1-6, 12 total)
-- Note: class_teacher_id set after staff rows exist
-- ============================================================
INSERT INTO sections (id, school_id, class_id, academic_year_id, name, class_teacher_id, max_strength, created_at)
VALUES
    -- Class 1
    ('032ec61c-3373-4389-9169-16ae826c357a', '926c372c-139d-460d-83b1-1a80ef92db57', 'a1000001-0000-0000-0000-000000000001', '8fd977b1-e495-42a5-8ef9-473aabd57ff5', 'A', 'e38bd555-29e6-4ab1-a725-f4d2687806ee', 50, NOW()),
    ('c0000001-0000-0000-0000-000000000001', '926c372c-139d-460d-83b1-1a80ef92db57', 'a1000001-0000-0000-0000-000000000001', '8fd977b1-e495-42a5-8ef9-473aabd57ff5', 'B', 'b0000001-0000-0000-0000-000000000001', 50, NOW()),
    -- Class 2
    ('64a5ca42-7480-46d2-83c1-9bd44a12dcb7', '926c372c-139d-460d-83b1-1a80ef92db57', 'a1000002-0000-0000-0000-000000000002', '8fd977b1-e495-42a5-8ef9-473aabd57ff5', 'A', 'b0000002-0000-0000-0000-000000000002', 50, NOW()),
    ('c0000002-0000-0000-0000-000000000002', '926c372c-139d-460d-83b1-1a80ef92db57', 'a1000002-0000-0000-0000-000000000002', '8fd977b1-e495-42a5-8ef9-473aabd57ff5', 'B', 'b0000003-0000-0000-0000-000000000003', 50, NOW()),
    -- Class 3
    ('c0000003-0000-0000-0000-000000000003', '926c372c-139d-460d-83b1-1a80ef92db57', 'a1000003-0000-0000-0000-000000000003', '8fd977b1-e495-42a5-8ef9-473aabd57ff5', 'A', 'b0000004-0000-0000-0000-000000000004', 50, NOW()),
    ('52bddd2c-a6f0-406f-8faf-2af17c349965', '926c372c-139d-460d-83b1-1a80ef92db57', 'a1000003-0000-0000-0000-000000000003', '8fd977b1-e495-42a5-8ef9-473aabd57ff5', 'B', 'b0000005-0000-0000-0000-000000000005', 50, NOW()),
    -- Class 4
    ('c0000004-0000-0000-0000-000000000004', '926c372c-139d-460d-83b1-1a80ef92db57', 'a1000004-0000-0000-0000-000000000004', '8fd977b1-e495-42a5-8ef9-473aabd57ff5', 'A', 'b0000006-0000-0000-0000-000000000006', 50, NOW()),
    ('c0000005-0000-0000-0000-000000000005', '926c372c-139d-460d-83b1-1a80ef92db57', 'a1000004-0000-0000-0000-000000000004', '8fd977b1-e495-42a5-8ef9-473aabd57ff5', 'B', 'b0000007-0000-0000-0000-000000000007', 50, NOW()),
    -- Class 5
    ('c0000006-0000-0000-0000-000000000006', '926c372c-139d-460d-83b1-1a80ef92db57', 'a1000005-0000-0000-0000-000000000005', '8fd977b1-e495-42a5-8ef9-473aabd57ff5', 'A', 'e38bd555-29e6-4ab1-a725-f4d2687806ee', 50, NOW()),
    ('c0000007-0000-0000-0000-000000000007', '926c372c-139d-460d-83b1-1a80ef92db57', 'a1000005-0000-0000-0000-000000000005', '8fd977b1-e495-42a5-8ef9-473aabd57ff5', 'B', 'b0000001-0000-0000-0000-000000000001', 50, NOW()),
    -- Class 6
    ('c0000008-0000-0000-0000-000000000008', '926c372c-139d-460d-83b1-1a80ef92db57', 'a1000006-0000-0000-0000-000000000006', '8fd977b1-e495-42a5-8ef9-473aabd57ff5', 'A', 'b0000002-0000-0000-0000-000000000002', 50, NOW()),
    ('c0000009-0000-0000-0000-000000000009', '926c372c-139d-460d-83b1-1a80ef92db57', 'a1000006-0000-0000-0000-000000000006', '8fd977b1-e495-42a5-8ef9-473aabd57ff5', 'B', 'b0000003-0000-0000-0000-000000000003', 50, NOW());

-- ============================================================
-- STUDENTS (4 in Class 1A, 3 each in other sections for attendance coverage)
-- ============================================================
INSERT INTO students (id, school_id, first_name, last_name, admission_number, date_of_birth, gender, is_active, created_at, updated_at)
VALUES
    -- Class 1A students
    ('d0000001-0000-0000-0000-000000000001', '926c372c-139d-460d-83b1-1a80ef92db57', 'Aarav',  'Sharma', 'VMS-001', '2018-05-10', 'MALE',   TRUE, NOW(), NOW()),
    ('d0000002-0000-0000-0000-000000000002', '926c372c-139d-460d-83b1-1a80ef92db57', 'Diya',   'Patel',  'VMS-002', '2018-06-15', 'FEMALE', TRUE, NOW(), NOW()),
    ('d0000003-0000-0000-0000-000000000003', '926c372c-139d-460d-83b1-1a80ef92db57', 'Ishaan', 'Verma',  'VMS-003', '2018-03-22', 'MALE',   TRUE, NOW(), NOW()),
    ('d0000004-0000-0000-0000-000000000004', '926c372c-139d-460d-83b1-1a80ef92db57', 'Kavya',  'Nair',   'VMS-004', '2018-07-08', 'FEMALE', TRUE, NOW(), NOW()),
    -- Class 1B students (3)
    ('d0000005-0000-0000-0000-000000000005', '926c372c-139d-460d-83b1-1a80ef92db57', 'Karan',  'Joshi',  'VMS-005', '2018-01-12', 'MALE',   TRUE, NOW(), NOW()),
    ('d0000006-0000-0000-0000-000000000006', '926c372c-139d-460d-83b1-1a80ef92db57', 'Riya',   'Shah',   'VMS-006', '2018-04-19', 'FEMALE', TRUE, NOW(), NOW()),
    ('d0000007-0000-0000-0000-000000000007', '926c372c-139d-460d-83b1-1a80ef92db57', 'Aryan',  'Singh',  'VMS-007', '2018-09-25', 'MALE',   TRUE, NOW(), NOW()),
    -- Class 2A students (3)
    ('d0000008-0000-0000-0000-000000000008', '926c372c-139d-460d-83b1-1a80ef92db57', 'Nisha',  'Kumar',  'VMS-008', '2017-02-14', 'FEMALE', TRUE, NOW(), NOW()),
    ('d0000009-0000-0000-0000-000000000009', '926c372c-139d-460d-83b1-1a80ef92db57', 'Vivek',  'Rao',    'VMS-009', '2017-11-30', 'MALE',   TRUE, NOW(), NOW()),
    ('d0000010-0000-0000-0000-000000000010', '926c372c-139d-460d-83b1-1a80ef92db57', 'Pooja',  'Mishra', 'VMS-010', '2017-08-05', 'FEMALE', TRUE, NOW(), NOW()),
    -- Class 2B students (3)
    ('d0000011-0000-0000-0000-000000000011', '926c372c-139d-460d-83b1-1a80ef92db57', 'Rahul',  'Gupta',  'VMS-011', '2017-06-18', 'MALE',   TRUE, NOW(), NOW()),
    ('d0000012-0000-0000-0000-000000000012', '926c372c-139d-460d-83b1-1a80ef92db57', 'Sneha',  'Tiwari', 'VMS-012', '2017-03-27', 'FEMALE', TRUE, NOW(), NOW()),
    ('d0000013-0000-0000-0000-000000000013', '926c372c-139d-460d-83b1-1a80ef92db57', 'Rohan',  'Desai',  'VMS-013', '2017-12-04', 'MALE',   TRUE, NOW(), NOW()),
    -- Class 3A students (3)
    ('d0000014-0000-0000-0000-000000000014', '926c372c-139d-460d-83b1-1a80ef92db57', 'Priya',  'Reddy',  'VMS-014', '2016-05-21', 'FEMALE', TRUE, NOW(), NOW()),
    ('d0000015-0000-0000-0000-000000000015', '926c372c-139d-460d-83b1-1a80ef92db57', 'Aditya', 'Bose',   'VMS-015', '2016-09-14', 'MALE',   TRUE, NOW(), NOW()),
    ('d0000016-0000-0000-0000-000000000016', '926c372c-139d-460d-83b1-1a80ef92db57', 'Trisha', 'Nambiar','VMS-016', '2016-07-30', 'FEMALE', TRUE, NOW(), NOW()),
    -- Class 3B students (3)
    ('d0000017-0000-0000-0000-000000000017', '926c372c-139d-460d-83b1-1a80ef92db57', 'Siddharth','Pillai','VMS-017', '2016-02-09', 'MALE',   TRUE, NOW(), NOW()),
    ('d0000018-0000-0000-0000-000000000018', '926c372c-139d-460d-83b1-1a80ef92db57', 'Aisha',  'Menon',  'VMS-018', '2016-11-17', 'FEMALE', TRUE, NOW(), NOW()),
    ('d0000019-0000-0000-0000-000000000019', '926c372c-139d-460d-83b1-1a80ef92db57', 'Dev',    'Kapoor', 'VMS-019', '2016-04-23', 'MALE',   TRUE, NOW(), NOW()),
    -- Class 4A students (3)
    ('d0000020-0000-0000-0000-000000000020', '926c372c-139d-460d-83b1-1a80ef92db57', 'Anita',  'Iyer',   'VMS-020', '2015-08-11', 'FEMALE', TRUE, NOW(), NOW()),
    ('d0000021-0000-0000-0000-000000000021', '926c372c-139d-460d-83b1-1a80ef92db57', 'Kabir',  'Sen',    'VMS-021', '2015-06-03', 'MALE',   TRUE, NOW(), NOW()),
    ('d0000022-0000-0000-0000-000000000022', '926c372c-139d-460d-83b1-1a80ef92db57', 'Meena',  'Sinha',  'VMS-022', '2015-01-28', 'FEMALE', TRUE, NOW(), NOW()),
    -- Class 4B students (3)
    ('d0000023-0000-0000-0000-000000000023', '926c372c-139d-460d-83b1-1a80ef92db57', 'Ajay',   'Choudhary','VMS-023','2015-10-16','MALE',   TRUE, NOW(), NOW()),
    ('d0000024-0000-0000-0000-000000000024', '926c372c-139d-460d-83b1-1a80ef92db57', 'Kavita', 'Pandey', 'VMS-024', '2015-03-07', 'FEMALE', TRUE, NOW(), NOW()),
    ('d0000025-0000-0000-0000-000000000025', '926c372c-139d-460d-83b1-1a80ef92db57', 'Sumit',  'Jain',   'VMS-025', '2015-12-20', 'MALE',   TRUE, NOW(), NOW()),
    -- Class 5A students (3)
    ('d0000026-0000-0000-0000-000000000026', '926c372c-139d-460d-83b1-1a80ef92db57', 'Radha',  'Pillai', 'VMS-026', '2014-04-05', 'FEMALE', TRUE, NOW(), NOW()),
    ('d0000027-0000-0000-0000-000000000027', '926c372c-139d-460d-83b1-1a80ef92db57', 'Nikhil', 'Nair',   'VMS-027', '2014-07-19', 'MALE',   TRUE, NOW(), NOW()),
    ('d0000028-0000-0000-0000-000000000028', '926c372c-139d-460d-83b1-1a80ef92db57', 'Shreya', 'Menon',  'VMS-028', '2014-09-02', 'FEMALE', TRUE, NOW(), NOW()),
    -- Class 5B students (3)
    ('d0000029-0000-0000-0000-000000000029', '926c372c-139d-460d-83b1-1a80ef92db57', 'Gaurav', 'Dubey',  'VMS-029', '2014-02-28', 'MALE',   TRUE, NOW(), NOW()),
    ('d0000030-0000-0000-0000-000000000030', '926c372c-139d-460d-83b1-1a80ef92db57', 'Tanvi',  'Kulkarni','VMS-030','2014-11-13','FEMALE', TRUE, NOW(), NOW()),
    ('d0000031-0000-0000-0000-000000000031', '926c372c-139d-460d-83b1-1a80ef92db57', 'Mohit',  'Varma',  'VMS-031', '2014-06-25', 'MALE',   TRUE, NOW(), NOW()),
    -- Class 6A students (3)
    ('d0000032-0000-0000-0000-000000000032', '926c372c-139d-460d-83b1-1a80ef92db57', 'Neha',   'Agarwal','VMS-032', '2013-03-17', 'FEMALE', TRUE, NOW(), NOW()),
    ('d0000033-0000-0000-0000-000000000033', '926c372c-139d-460d-83b1-1a80ef92db57', 'Akash',  'Malhotra','VMS-033','2013-08-22','MALE',   TRUE, NOW(), NOW()),
    ('d0000034-0000-0000-0000-000000000034', '926c372c-139d-460d-83b1-1a80ef92db57', 'Ritika', 'Saxena', 'VMS-034', '2013-05-09', 'FEMALE', TRUE, NOW(), NOW()),
    -- Class 6B students (3)
    ('d0000035-0000-0000-0000-000000000035', '926c372c-139d-460d-83b1-1a80ef92db57', 'Varun',  'Tomar',  'VMS-035', '2013-01-31', 'MALE',   TRUE, NOW(), NOW()),
    ('d0000036-0000-0000-0000-000000000036', '926c372c-139d-460d-83b1-1a80ef92db57', 'Poonam', 'Rawat',  'VMS-036', '2013-10-06', 'FEMALE', TRUE, NOW(), NOW()),
    ('d0000037-0000-0000-0000-000000000037', '926c372c-139d-460d-83b1-1a80ef92db57', 'Harish', 'Yadav',  'VMS-037', '2013-12-14', 'MALE',   TRUE, NOW(), NOW());

-- ============================================================
-- STUDENT ENROLLMENTS (one per student per academic year)
-- ============================================================
INSERT INTO student_enrollments (id, student_id, school_id, academic_year_id, section_id, roll_number, status, created_at)
VALUES
    -- Class 1A (4 students)
    (gen_random_uuid(), 'd0000001-0000-0000-0000-000000000001', '926c372c-139d-460d-83b1-1a80ef92db57', '8fd977b1-e495-42a5-8ef9-473aabd57ff5', '032ec61c-3373-4389-9169-16ae826c357a', 1,  'ACTIVE', NOW()),
    (gen_random_uuid(), 'd0000002-0000-0000-0000-000000000002', '926c372c-139d-460d-83b1-1a80ef92db57', '8fd977b1-e495-42a5-8ef9-473aabd57ff5', '032ec61c-3373-4389-9169-16ae826c357a', 2,  'ACTIVE', NOW()),
    (gen_random_uuid(), 'd0000003-0000-0000-0000-000000000003', '926c372c-139d-460d-83b1-1a80ef92db57', '8fd977b1-e495-42a5-8ef9-473aabd57ff5', '032ec61c-3373-4389-9169-16ae826c357a', 3,  'ACTIVE', NOW()),
    (gen_random_uuid(), 'd0000004-0000-0000-0000-000000000004', '926c372c-139d-460d-83b1-1a80ef92db57', '8fd977b1-e495-42a5-8ef9-473aabd57ff5', '032ec61c-3373-4389-9169-16ae826c357a', 4,  'ACTIVE', NOW()),
    -- Class 1B (3 students)
    (gen_random_uuid(), 'd0000005-0000-0000-0000-000000000005', '926c372c-139d-460d-83b1-1a80ef92db57', '8fd977b1-e495-42a5-8ef9-473aabd57ff5', 'c0000001-0000-0000-0000-000000000001', 1,  'ACTIVE', NOW()),
    (gen_random_uuid(), 'd0000006-0000-0000-0000-000000000006', '926c372c-139d-460d-83b1-1a80ef92db57', '8fd977b1-e495-42a5-8ef9-473aabd57ff5', 'c0000001-0000-0000-0000-000000000001', 2,  'ACTIVE', NOW()),
    (gen_random_uuid(), 'd0000007-0000-0000-0000-000000000007', '926c372c-139d-460d-83b1-1a80ef92db57', '8fd977b1-e495-42a5-8ef9-473aabd57ff5', 'c0000001-0000-0000-0000-000000000001', 3,  'ACTIVE', NOW()),
    -- Class 2A (3 students)
    (gen_random_uuid(), 'd0000008-0000-0000-0000-000000000008', '926c372c-139d-460d-83b1-1a80ef92db57', '8fd977b1-e495-42a5-8ef9-473aabd57ff5', '64a5ca42-7480-46d2-83c1-9bd44a12dcb7', 1,  'ACTIVE', NOW()),
    (gen_random_uuid(), 'd0000009-0000-0000-0000-000000000009', '926c372c-139d-460d-83b1-1a80ef92db57', '8fd977b1-e495-42a5-8ef9-473aabd57ff5', '64a5ca42-7480-46d2-83c1-9bd44a12dcb7', 2,  'ACTIVE', NOW()),
    (gen_random_uuid(), 'd0000010-0000-0000-0000-000000000010', '926c372c-139d-460d-83b1-1a80ef92db57', '8fd977b1-e495-42a5-8ef9-473aabd57ff5', '64a5ca42-7480-46d2-83c1-9bd44a12dcb7', 3,  'ACTIVE', NOW()),
    -- Class 2B (3 students)
    (gen_random_uuid(), 'd0000011-0000-0000-0000-000000000011', '926c372c-139d-460d-83b1-1a80ef92db57', '8fd977b1-e495-42a5-8ef9-473aabd57ff5', 'c0000002-0000-0000-0000-000000000002', 1,  'ACTIVE', NOW()),
    (gen_random_uuid(), 'd0000012-0000-0000-0000-000000000012', '926c372c-139d-460d-83b1-1a80ef92db57', '8fd977b1-e495-42a5-8ef9-473aabd57ff5', 'c0000002-0000-0000-0000-000000000002', 2,  'ACTIVE', NOW()),
    (gen_random_uuid(), 'd0000013-0000-0000-0000-000000000013', '926c372c-139d-460d-83b1-1a80ef92db57', '8fd977b1-e495-42a5-8ef9-473aabd57ff5', 'c0000002-0000-0000-0000-000000000002', 3,  'ACTIVE', NOW()),
    -- Class 3A (3 students)
    (gen_random_uuid(), 'd0000014-0000-0000-0000-000000000014', '926c372c-139d-460d-83b1-1a80ef92db57', '8fd977b1-e495-42a5-8ef9-473aabd57ff5', 'c0000003-0000-0000-0000-000000000003', 1,  'ACTIVE', NOW()),
    (gen_random_uuid(), 'd0000015-0000-0000-0000-000000000015', '926c372c-139d-460d-83b1-1a80ef92db57', '8fd977b1-e495-42a5-8ef9-473aabd57ff5', 'c0000003-0000-0000-0000-000000000003', 2,  'ACTIVE', NOW()),
    (gen_random_uuid(), 'd0000016-0000-0000-0000-000000000016', '926c372c-139d-460d-83b1-1a80ef92db57', '8fd977b1-e495-42a5-8ef9-473aabd57ff5', 'c0000003-0000-0000-0000-000000000003', 3,  'ACTIVE', NOW()),
    -- Class 3B (3 students)
    (gen_random_uuid(), 'd0000017-0000-0000-0000-000000000017', '926c372c-139d-460d-83b1-1a80ef92db57', '8fd977b1-e495-42a5-8ef9-473aabd57ff5', '52bddd2c-a6f0-406f-8faf-2af17c349965', 1,  'ACTIVE', NOW()),
    (gen_random_uuid(), 'd0000018-0000-0000-0000-000000000018', '926c372c-139d-460d-83b1-1a80ef92db57', '8fd977b1-e495-42a5-8ef9-473aabd57ff5', '52bddd2c-a6f0-406f-8faf-2af17c349965', 2,  'ACTIVE', NOW()),
    (gen_random_uuid(), 'd0000019-0000-0000-0000-000000000019', '926c372c-139d-460d-83b1-1a80ef92db57', '8fd977b1-e495-42a5-8ef9-473aabd57ff5', '52bddd2c-a6f0-406f-8faf-2af17c349965', 3,  'ACTIVE', NOW()),
    -- Class 4A (3 students)
    (gen_random_uuid(), 'd0000020-0000-0000-0000-000000000020', '926c372c-139d-460d-83b1-1a80ef92db57', '8fd977b1-e495-42a5-8ef9-473aabd57ff5', 'c0000004-0000-0000-0000-000000000004', 1,  'ACTIVE', NOW()),
    (gen_random_uuid(), 'd0000021-0000-0000-0000-000000000021', '926c372c-139d-460d-83b1-1a80ef92db57', '8fd977b1-e495-42a5-8ef9-473aabd57ff5', 'c0000004-0000-0000-0000-000000000004', 2,  'ACTIVE', NOW()),
    (gen_random_uuid(), 'd0000022-0000-0000-0000-000000000022', '926c372c-139d-460d-83b1-1a80ef92db57', '8fd977b1-e495-42a5-8ef9-473aabd57ff5', 'c0000004-0000-0000-0000-000000000004', 3,  'ACTIVE', NOW()),
    -- Class 4B (3 students)
    (gen_random_uuid(), 'd0000023-0000-0000-0000-000000000023', '926c372c-139d-460d-83b1-1a80ef92db57', '8fd977b1-e495-42a5-8ef9-473aabd57ff5', 'c0000005-0000-0000-0000-000000000005', 1,  'ACTIVE', NOW()),
    (gen_random_uuid(), 'd0000024-0000-0000-0000-000000000024', '926c372c-139d-460d-83b1-1a80ef92db57', '8fd977b1-e495-42a5-8ef9-473aabd57ff5', 'c0000005-0000-0000-0000-000000000005', 2,  'ACTIVE', NOW()),
    (gen_random_uuid(), 'd0000025-0000-0000-0000-000000000025', '926c372c-139d-460d-83b1-1a80ef92db57', '8fd977b1-e495-42a5-8ef9-473aabd57ff5', 'c0000005-0000-0000-0000-000000000005', 3,  'ACTIVE', NOW()),
    -- Class 5A (3 students)
    (gen_random_uuid(), 'd0000026-0000-0000-0000-000000000026', '926c372c-139d-460d-83b1-1a80ef92db57', '8fd977b1-e495-42a5-8ef9-473aabd57ff5', 'c0000006-0000-0000-0000-000000000006', 1,  'ACTIVE', NOW()),
    (gen_random_uuid(), 'd0000027-0000-0000-0000-000000000027', '926c372c-139d-460d-83b1-1a80ef92db57', '8fd977b1-e495-42a5-8ef9-473aabd57ff5', 'c0000006-0000-0000-0000-000000000006', 2,  'ACTIVE', NOW()),
    (gen_random_uuid(), 'd0000028-0000-0000-0000-000000000028', '926c372c-139d-460d-83b1-1a80ef92db57', '8fd977b1-e495-42a5-8ef9-473aabd57ff5', 'c0000006-0000-0000-0000-000000000006', 3,  'ACTIVE', NOW()),
    -- Class 5B (3 students)
    (gen_random_uuid(), 'd0000029-0000-0000-0000-000000000029', '926c372c-139d-460d-83b1-1a80ef92db57', '8fd977b1-e495-42a5-8ef9-473aabd57ff5', 'c0000007-0000-0000-0000-000000000007', 1,  'ACTIVE', NOW()),
    (gen_random_uuid(), 'd0000030-0000-0000-0000-000000000030', '926c372c-139d-460d-83b1-1a80ef92db57', '8fd977b1-e495-42a5-8ef9-473aabd57ff5', 'c0000007-0000-0000-0000-000000000007', 2,  'ACTIVE', NOW()),
    (gen_random_uuid(), 'd0000031-0000-0000-0000-000000000031', '926c372c-139d-460d-83b1-1a80ef92db57', '8fd977b1-e495-42a5-8ef9-473aabd57ff5', 'c0000007-0000-0000-0000-000000000007', 3,  'ACTIVE', NOW()),
    -- Class 6A (3 students)
    (gen_random_uuid(), 'd0000032-0000-0000-0000-000000000032', '926c372c-139d-460d-83b1-1a80ef92db57', '8fd977b1-e495-42a5-8ef9-473aabd57ff5', 'c0000008-0000-0000-0000-000000000008', 1,  'ACTIVE', NOW()),
    (gen_random_uuid(), 'd0000033-0000-0000-0000-000000000033', '926c372c-139d-460d-83b1-1a80ef92db57', '8fd977b1-e495-42a5-8ef9-473aabd57ff5', 'c0000008-0000-0000-0000-000000000008', 2,  'ACTIVE', NOW()),
    (gen_random_uuid(), 'd0000034-0000-0000-0000-000000000034', '926c372c-139d-460d-83b1-1a80ef92db57', '8fd977b1-e495-42a5-8ef9-473aabd57ff5', 'c0000008-0000-0000-0000-000000000008', 3,  'ACTIVE', NOW()),
    -- Class 6B (3 students)
    (gen_random_uuid(), 'd0000035-0000-0000-0000-000000000035', '926c372c-139d-460d-83b1-1a80ef92db57', '8fd977b1-e495-42a5-8ef9-473aabd57ff5', 'c0000009-0000-0000-0000-000000000009', 1,  'ACTIVE', NOW()),
    (gen_random_uuid(), 'd0000036-0000-0000-0000-000000000036', '926c372c-139d-460d-83b1-1a80ef92db57', '8fd977b1-e495-42a5-8ef9-473aabd57ff5', 'c0000009-0000-0000-0000-000000000009', 2,  'ACTIVE', NOW()),
    (gen_random_uuid(), 'd0000037-0000-0000-0000-000000000037', '926c372c-139d-460d-83b1-1a80ef92db57', '8fd977b1-e495-42a5-8ef9-473aabd57ff5', 'c0000009-0000-0000-0000-000000000009', 3,  'ACTIVE', NOW());

-- ============================================================
-- TIMETABLE PERIODS (P1-P8 + lunch break)
-- ============================================================
INSERT INTO timetable_periods (id, school_id, name, start_time, end_time, sort_order, is_break, created_at, updated_at)
VALUES
    ('f010685d-74f8-49f8-baac-55d9afc8f727', '926c372c-139d-460d-83b1-1a80ef92db57', 'Period 1', '08:00', '08:45', 1,  FALSE, NOW(), NOW()),
    ('00000002-0000-0000-0000-000000000002', '926c372c-139d-460d-83b1-1a80ef92db57', 'Period 2', '08:50', '09:35', 2,  FALSE, NOW(), NOW()),
    ('00000003-0000-0000-0000-000000000003', '926c372c-139d-460d-83b1-1a80ef92db57', 'Period 3', '09:40', '10:25', 3,  FALSE, NOW(), NOW()),
    ('00000004-0000-0000-0000-000000000004', '926c372c-139d-460d-83b1-1a80ef92db57', 'Lunch',    '10:25', '11:05', 4,  TRUE,  NOW(), NOW()),
    ('56088684-b11d-4739-ba97-45a6991253f5', '926c372c-139d-460d-83b1-1a80ef92db57', 'Period 4', '11:05', '11:50', 5,  FALSE, NOW(), NOW()),
    ('00000006-0000-0000-0000-000000000006', '926c372c-139d-460d-83b1-1a80ef92db57', 'Period 5', '11:55', '12:40', 6,  FALSE, NOW(), NOW()),
    ('00000007-0000-0000-0000-000000000007', '926c372c-139d-460d-83b1-1a80ef92db57', 'Period 6', '12:45', '13:30', 7,  FALSE, NOW(), NOW()),
    ('00000008-0000-0000-0000-000000000008', '926c372c-139d-460d-83b1-1a80ef92db57', 'Period 7', '13:35', '14:20', 8,  FALSE, NOW(), NOW()),
    ('00000009-0000-0000-0000-000000000009', '926c372c-139d-460d-83b1-1a80ef92db57', 'Period 8', '14:25', '15:10', 9,  FALSE, NOW(), NOW());

-- ============================================================
-- TIMETABLE ENTRIES
-- Ananya Singh (e38bd555) in Class 1A P1-P3, Mon-Fri
-- Ananya Singh also in Class 3B P4, Mon-Fri
-- Other teachers distributed across sections
-- day_of_week: 1=Mon, 2=Tue, 3=Wed, 4=Thu, 5=Fri
-- ============================================================
INSERT INTO timetable_entries (id, school_id, section_id, period_id, day_of_week, teacher_id, created_at, updated_at)
VALUES
    -- Ananya Singh in Class 1A, Period 1, Mon-Fri
    (gen_random_uuid(), '926c372c-139d-460d-83b1-1a80ef92db57', '032ec61c-3373-4389-9169-16ae826c357a', 'f010685d-74f8-49f8-baac-55d9afc8f727', 1, 'e38bd555-29e6-4ab1-a725-f4d2687806ee', NOW(), NOW()),
    (gen_random_uuid(), '926c372c-139d-460d-83b1-1a80ef92db57', '032ec61c-3373-4389-9169-16ae826c357a', 'f010685d-74f8-49f8-baac-55d9afc8f727', 2, 'e38bd555-29e6-4ab1-a725-f4d2687806ee', NOW(), NOW()),
    (gen_random_uuid(), '926c372c-139d-460d-83b1-1a80ef92db57', '032ec61c-3373-4389-9169-16ae826c357a', 'f010685d-74f8-49f8-baac-55d9afc8f727', 3, 'e38bd555-29e6-4ab1-a725-f4d2687806ee', NOW(), NOW()),
    (gen_random_uuid(), '926c372c-139d-460d-83b1-1a80ef92db57', '032ec61c-3373-4389-9169-16ae826c357a', 'f010685d-74f8-49f8-baac-55d9afc8f727', 4, 'e38bd555-29e6-4ab1-a725-f4d2687806ee', NOW(), NOW()),
    (gen_random_uuid(), '926c372c-139d-460d-83b1-1a80ef92db57', '032ec61c-3373-4389-9169-16ae826c357a', 'f010685d-74f8-49f8-baac-55d9afc8f727', 5, 'e38bd555-29e6-4ab1-a725-f4d2687806ee', NOW(), NOW()),
    -- Ananya Singh in Class 1A, Period 2, Mon-Fri
    (gen_random_uuid(), '926c372c-139d-460d-83b1-1a80ef92db57', '032ec61c-3373-4389-9169-16ae826c357a', '00000002-0000-0000-0000-000000000002', 1, 'e38bd555-29e6-4ab1-a725-f4d2687806ee', NOW(), NOW()),
    (gen_random_uuid(), '926c372c-139d-460d-83b1-1a80ef92db57', '032ec61c-3373-4389-9169-16ae826c357a', '00000002-0000-0000-0000-000000000002', 2, 'e38bd555-29e6-4ab1-a725-f4d2687806ee', NOW(), NOW()),
    (gen_random_uuid(), '926c372c-139d-460d-83b1-1a80ef92db57', '032ec61c-3373-4389-9169-16ae826c357a', '00000002-0000-0000-0000-000000000002', 3, 'e38bd555-29e6-4ab1-a725-f4d2687806ee', NOW(), NOW()),
    (gen_random_uuid(), '926c372c-139d-460d-83b1-1a80ef92db57', '032ec61c-3373-4389-9169-16ae826c357a', '00000002-0000-0000-0000-000000000002', 4, 'e38bd555-29e6-4ab1-a725-f4d2687806ee', NOW(), NOW()),
    (gen_random_uuid(), '926c372c-139d-460d-83b1-1a80ef92db57', '032ec61c-3373-4389-9169-16ae826c357a', '00000002-0000-0000-0000-000000000002', 5, 'e38bd555-29e6-4ab1-a725-f4d2687806ee', NOW(), NOW()),
    -- Ananya Singh in Class 1A, Period 3, Mon-Fri
    (gen_random_uuid(), '926c372c-139d-460d-83b1-1a80ef92db57', '032ec61c-3373-4389-9169-16ae826c357a', '00000003-0000-0000-0000-000000000003', 1, 'e38bd555-29e6-4ab1-a725-f4d2687806ee', NOW(), NOW()),
    (gen_random_uuid(), '926c372c-139d-460d-83b1-1a80ef92db57', '032ec61c-3373-4389-9169-16ae826c357a', '00000003-0000-0000-0000-000000000003', 2, 'e38bd555-29e6-4ab1-a725-f4d2687806ee', NOW(), NOW()),
    (gen_random_uuid(), '926c372c-139d-460d-83b1-1a80ef92db57', '032ec61c-3373-4389-9169-16ae826c357a', '00000003-0000-0000-0000-000000000003', 3, 'e38bd555-29e6-4ab1-a725-f4d2687806ee', NOW(), NOW()),
    (gen_random_uuid(), '926c372c-139d-460d-83b1-1a80ef92db57', '032ec61c-3373-4389-9169-16ae826c357a', '00000003-0000-0000-0000-000000000003', 4, 'e38bd555-29e6-4ab1-a725-f4d2687806ee', NOW(), NOW()),
    (gen_random_uuid(), '926c372c-139d-460d-83b1-1a80ef92db57', '032ec61c-3373-4389-9169-16ae826c357a', '00000003-0000-0000-0000-000000000003', 5, 'e38bd555-29e6-4ab1-a725-f4d2687806ee', NOW(), NOW()),
    -- Ananya Singh in Class 3B, Period 4, Mon-Fri (cross-section)
    (gen_random_uuid(), '926c372c-139d-460d-83b1-1a80ef92db57', '52bddd2c-a6f0-406f-8faf-2af17c349965', '56088684-b11d-4739-ba97-45a6991253f5', 1, 'e38bd555-29e6-4ab1-a725-f4d2687806ee', NOW(), NOW()),
    (gen_random_uuid(), '926c372c-139d-460d-83b1-1a80ef92db57', '52bddd2c-a6f0-406f-8faf-2af17c349965', '56088684-b11d-4739-ba97-45a6991253f5', 2, 'e38bd555-29e6-4ab1-a725-f4d2687806ee', NOW(), NOW()),
    (gen_random_uuid(), '926c372c-139d-460d-83b1-1a80ef92db57', '52bddd2c-a6f0-406f-8faf-2af17c349965', '56088684-b11d-4739-ba97-45a6991253f5', 3, 'e38bd555-29e6-4ab1-a725-f4d2687806ee', NOW(), NOW()),
    (gen_random_uuid(), '926c372c-139d-460d-83b1-1a80ef92db57', '52bddd2c-a6f0-406f-8faf-2af17c349965', '56088684-b11d-4739-ba97-45a6991253f5', 4, 'e38bd555-29e6-4ab1-a725-f4d2687806ee', NOW(), NOW()),
    (gen_random_uuid(), '926c372c-139d-460d-83b1-1a80ef92db57', '52bddd2c-a6f0-406f-8faf-2af17c349965', '56088684-b11d-4739-ba97-45a6991253f5', 5, 'e38bd555-29e6-4ab1-a725-f4d2687806ee', NOW(), NOW()),
    -- Other teachers in Class 1A (P4, P5 — so it's not empty)
    (gen_random_uuid(), '926c372c-139d-460d-83b1-1a80ef92db57', '032ec61c-3373-4389-9169-16ae826c357a', '56088684-b11d-4739-ba97-45a6991253f5', 1, 'b0000001-0000-0000-0000-000000000001', NOW(), NOW()),
    (gen_random_uuid(), '926c372c-139d-460d-83b1-1a80ef92db57', '032ec61c-3373-4389-9169-16ae826c357a', '56088684-b11d-4739-ba97-45a6991253f5', 2, 'b0000001-0000-0000-0000-000000000001', NOW(), NOW()),
    (gen_random_uuid(), '926c372c-139d-460d-83b1-1a80ef92db57', '032ec61c-3373-4389-9169-16ae826c357a', '56088684-b11d-4739-ba97-45a6991253f5', 3, 'b0000001-0000-0000-0000-000000000001', NOW(), NOW()),
    (gen_random_uuid(), '926c372c-139d-460d-83b1-1a80ef92db57', '032ec61c-3373-4389-9169-16ae826c357a', '56088684-b11d-4739-ba97-45a6991253f5', 4, 'b0000001-0000-0000-0000-000000000001', NOW(), NOW()),
    (gen_random_uuid(), '926c372c-139d-460d-83b1-1a80ef92db57', '032ec61c-3373-4389-9169-16ae826c357a', '56088684-b11d-4739-ba97-45a6991253f5', 5, 'b0000001-0000-0000-0000-000000000001', NOW(), NOW()),
    -- Deepa Nair (b0000002) in Class 2A all periods (P1-P3, P5-P8) Mon-Fri so she's free in P4
    (gen_random_uuid(), '926c372c-139d-460d-83b1-1a80ef92db57', '64a5ca42-7480-46d2-83c1-9bd44a12dcb7', 'f010685d-74f8-49f8-baac-55d9afc8f727', 1, 'b0000002-0000-0000-0000-000000000002', NOW(), NOW()),
    (gen_random_uuid(), '926c372c-139d-460d-83b1-1a80ef92db57', '64a5ca42-7480-46d2-83c1-9bd44a12dcb7', 'f010685d-74f8-49f8-baac-55d9afc8f727', 2, 'b0000002-0000-0000-0000-000000000002', NOW(), NOW()),
    (gen_random_uuid(), '926c372c-139d-460d-83b1-1a80ef92db57', '64a5ca42-7480-46d2-83c1-9bd44a12dcb7', 'f010685d-74f8-49f8-baac-55d9afc8f727', 3, 'b0000002-0000-0000-0000-000000000002', NOW(), NOW()),
    (gen_random_uuid(), '926c372c-139d-460d-83b1-1a80ef92db57', '64a5ca42-7480-46d2-83c1-9bd44a12dcb7', 'f010685d-74f8-49f8-baac-55d9afc8f727', 4, 'b0000002-0000-0000-0000-000000000002', NOW(), NOW()),
    (gen_random_uuid(), '926c372c-139d-460d-83b1-1a80ef92db57', '64a5ca42-7480-46d2-83c1-9bd44a12dcb7', 'f010685d-74f8-49f8-baac-55d9afc8f727', 5, 'b0000002-0000-0000-0000-000000000002', NOW(), NOW()),
    -- Class 2A Period 5 Mon — left unassigned so TC10 can assign Deepa Nair there
    -- Assign Deepa to P2, P3 in 2A so she appears:
    (gen_random_uuid(), '926c372c-139d-460d-83b1-1a80ef92db57', '64a5ca42-7480-46d2-83c1-9bd44a12dcb7', '00000002-0000-0000-0000-000000000002', 1, 'b0000002-0000-0000-0000-000000000002', NOW(), NOW()),
    (gen_random_uuid(), '926c372c-139d-460d-83b1-1a80ef92db57', '64a5ca42-7480-46d2-83c1-9bd44a12dcb7', '00000002-0000-0000-0000-000000000002', 2, 'b0000002-0000-0000-0000-000000000002', NOW(), NOW()),
    (gen_random_uuid(), '926c372c-139d-460d-83b1-1a80ef92db57', '64a5ca42-7480-46d2-83c1-9bd44a12dcb7', '00000002-0000-0000-0000-000000000002', 3, 'b0000002-0000-0000-0000-000000000002', NOW(), NOW()),
    (gen_random_uuid(), '926c372c-139d-460d-83b1-1a80ef92db57', '64a5ca42-7480-46d2-83c1-9bd44a12dcb7', '00000002-0000-0000-0000-000000000002', 4, 'b0000002-0000-0000-0000-000000000002', NOW(), NOW()),
    (gen_random_uuid(), '926c372c-139d-460d-83b1-1a80ef92db57', '64a5ca42-7480-46d2-83c1-9bd44a12dcb7', '00000002-0000-0000-0000-000000000002', 5, 'b0000002-0000-0000-0000-000000000002', NOW(), NOW()),
    (gen_random_uuid(), '926c372c-139d-460d-83b1-1a80ef92db57', '64a5ca42-7480-46d2-83c1-9bd44a12dcb7', '00000003-0000-0000-0000-000000000003', 1, 'b0000002-0000-0000-0000-000000000002', NOW(), NOW()),
    (gen_random_uuid(), '926c372c-139d-460d-83b1-1a80ef92db57', '64a5ca42-7480-46d2-83c1-9bd44a12dcb7', '00000003-0000-0000-0000-000000000003', 2, 'b0000002-0000-0000-0000-000000000002', NOW(), NOW()),
    (gen_random_uuid(), '926c372c-139d-460d-83b1-1a80ef92db57', '64a5ca42-7480-46d2-83c1-9bd44a12dcb7', '00000003-0000-0000-0000-000000000003', 3, 'b0000002-0000-0000-0000-000000000002', NOW(), NOW()),
    (gen_random_uuid(), '926c372c-139d-460d-83b1-1a80ef92db57', '64a5ca42-7480-46d2-83c1-9bd44a12dcb7', '00000003-0000-0000-0000-000000000003', 4, 'b0000002-0000-0000-0000-000000000002', NOW(), NOW()),
    (gen_random_uuid(), '926c372c-139d-460d-83b1-1a80ef92db57', '64a5ca42-7480-46d2-83c1-9bd44a12dcb7', '00000003-0000-0000-0000-000000000003', 5, 'b0000002-0000-0000-0000-000000000002', NOW(), NOW());

-- ============================================================
-- ATTENDANCE RECORDS for 2026-05-27
-- Class 1A: Aarav=LATE, Diya=PRESENT, Ishaan=PRESENT, Kavya=ABSENT
-- Other sections: all PRESENT (for dashboard stats)
-- ============================================================
INSERT INTO attendance_records (id, school_id, student_id, section_id, date, status, marked_by_id, created_at, updated_at)
VALUES
    -- Class 1A
    (gen_random_uuid(), '926c372c-139d-460d-83b1-1a80ef92db57', 'd0000001-0000-0000-0000-000000000001', '032ec61c-3373-4389-9169-16ae826c357a', '2026-05-27', 'LATE',    'e38bd555-29e6-4ab1-a725-f4d2687806ee', NOW(), NOW()),
    (gen_random_uuid(), '926c372c-139d-460d-83b1-1a80ef92db57', 'd0000002-0000-0000-0000-000000000002', '032ec61c-3373-4389-9169-16ae826c357a', '2026-05-27', 'PRESENT', 'e38bd555-29e6-4ab1-a725-f4d2687806ee', NOW(), NOW()),
    (gen_random_uuid(), '926c372c-139d-460d-83b1-1a80ef92db57', 'd0000003-0000-0000-0000-000000000003', '032ec61c-3373-4389-9169-16ae826c357a', '2026-05-27', 'PRESENT', 'e38bd555-29e6-4ab1-a725-f4d2687806ee', NOW(), NOW()),
    (gen_random_uuid(), '926c372c-139d-460d-83b1-1a80ef92db57', 'd0000004-0000-0000-0000-000000000004', '032ec61c-3373-4389-9169-16ae826c357a', '2026-05-27', 'ABSENT',  'e38bd555-29e6-4ab1-a725-f4d2687806ee', NOW(), NOW()),
    -- Class 1B
    (gen_random_uuid(), '926c372c-139d-460d-83b1-1a80ef92db57', 'd0000005-0000-0000-0000-000000000005', 'c0000001-0000-0000-0000-000000000001', '2026-05-27', 'PRESENT', 'b0000001-0000-0000-0000-000000000001', NOW(), NOW()),
    (gen_random_uuid(), '926c372c-139d-460d-83b1-1a80ef92db57', 'd0000006-0000-0000-0000-000000000006', 'c0000001-0000-0000-0000-000000000001', '2026-05-27', 'PRESENT', 'b0000001-0000-0000-0000-000000000001', NOW(), NOW()),
    (gen_random_uuid(), '926c372c-139d-460d-83b1-1a80ef92db57', 'd0000007-0000-0000-0000-000000000007', 'c0000001-0000-0000-0000-000000000001', '2026-05-27', 'LATE',    'b0000001-0000-0000-0000-000000000001', NOW(), NOW()),
    -- Class 2A
    (gen_random_uuid(), '926c372c-139d-460d-83b1-1a80ef92db57', 'd0000008-0000-0000-0000-000000000008', '64a5ca42-7480-46d2-83c1-9bd44a12dcb7', '2026-05-27', 'PRESENT', 'b0000002-0000-0000-0000-000000000002', NOW(), NOW()),
    (gen_random_uuid(), '926c372c-139d-460d-83b1-1a80ef92db57', 'd0000009-0000-0000-0000-000000000009', '64a5ca42-7480-46d2-83c1-9bd44a12dcb7', '2026-05-27', 'PRESENT', 'b0000002-0000-0000-0000-000000000002', NOW(), NOW()),
    (gen_random_uuid(), '926c372c-139d-460d-83b1-1a80ef92db57', 'd0000010-0000-0000-0000-000000000010', '64a5ca42-7480-46d2-83c1-9bd44a12dcb7', '2026-05-27', 'ABSENT',  'b0000002-0000-0000-0000-000000000002', NOW(), NOW()),
    -- Class 2B
    (gen_random_uuid(), '926c372c-139d-460d-83b1-1a80ef92db57', 'd0000011-0000-0000-0000-000000000011', 'c0000002-0000-0000-0000-000000000002', '2026-05-27', 'PRESENT', 'b0000003-0000-0000-0000-000000000003', NOW(), NOW()),
    (gen_random_uuid(), '926c372c-139d-460d-83b1-1a80ef92db57', 'd0000012-0000-0000-0000-000000000012', 'c0000002-0000-0000-0000-000000000002', '2026-05-27', 'PRESENT', 'b0000003-0000-0000-0000-000000000003', NOW(), NOW()),
    (gen_random_uuid(), '926c372c-139d-460d-83b1-1a80ef92db57', 'd0000013-0000-0000-0000-000000000013', 'c0000002-0000-0000-0000-000000000002', '2026-05-27', 'LATE',    'b0000003-0000-0000-0000-000000000003', NOW(), NOW()),
    -- Class 3A
    (gen_random_uuid(), '926c372c-139d-460d-83b1-1a80ef92db57', 'd0000014-0000-0000-0000-000000000014', 'c0000003-0000-0000-0000-000000000003', '2026-05-27', 'PRESENT', 'b0000004-0000-0000-0000-000000000004', NOW(), NOW()),
    (gen_random_uuid(), '926c372c-139d-460d-83b1-1a80ef92db57', 'd0000015-0000-0000-0000-000000000015', 'c0000003-0000-0000-0000-000000000003', '2026-05-27', 'PRESENT', 'b0000004-0000-0000-0000-000000000004', NOW(), NOW()),
    (gen_random_uuid(), '926c372c-139d-460d-83b1-1a80ef92db57', 'd0000016-0000-0000-0000-000000000016', 'c0000003-0000-0000-0000-000000000003', '2026-05-27', 'ABSENT',  'b0000004-0000-0000-0000-000000000004', NOW(), NOW()),
    -- Class 3B
    (gen_random_uuid(), '926c372c-139d-460d-83b1-1a80ef92db57', 'd0000017-0000-0000-0000-000000000017', '52bddd2c-a6f0-406f-8faf-2af17c349965', '2026-05-27', 'PRESENT', 'b0000005-0000-0000-0000-000000000005', NOW(), NOW()),
    (gen_random_uuid(), '926c372c-139d-460d-83b1-1a80ef92db57', 'd0000018-0000-0000-0000-000000000018', '52bddd2c-a6f0-406f-8faf-2af17c349965', '2026-05-27', 'PRESENT', 'b0000005-0000-0000-0000-000000000005', NOW(), NOW()),
    (gen_random_uuid(), '926c372c-139d-460d-83b1-1a80ef92db57', 'd0000019-0000-0000-0000-000000000019', '52bddd2c-a6f0-406f-8faf-2af17c349965', '2026-05-27', 'LATE',    'b0000005-0000-0000-0000-000000000005', NOW(), NOW()),
    -- Class 4A
    (gen_random_uuid(), '926c372c-139d-460d-83b1-1a80ef92db57', 'd0000020-0000-0000-0000-000000000020', 'c0000004-0000-0000-0000-000000000004', '2026-05-27', 'PRESENT', 'b0000006-0000-0000-0000-000000000006', NOW(), NOW()),
    (gen_random_uuid(), '926c372c-139d-460d-83b1-1a80ef92db57', 'd0000021-0000-0000-0000-000000000021', 'c0000004-0000-0000-0000-000000000004', '2026-05-27', 'PRESENT', 'b0000006-0000-0000-0000-000000000006', NOW(), NOW()),
    (gen_random_uuid(), '926c372c-139d-460d-83b1-1a80ef92db57', 'd0000022-0000-0000-0000-000000000022', 'c0000004-0000-0000-0000-000000000004', '2026-05-27', 'PRESENT', 'b0000006-0000-0000-0000-000000000006', NOW(), NOW()),
    -- Class 4B
    (gen_random_uuid(), '926c372c-139d-460d-83b1-1a80ef92db57', 'd0000023-0000-0000-0000-000000000023', 'c0000005-0000-0000-0000-000000000005', '2026-05-27', 'PRESENT', 'b0000007-0000-0000-0000-000000000007', NOW(), NOW()),
    (gen_random_uuid(), '926c372c-139d-460d-83b1-1a80ef92db57', 'd0000024-0000-0000-0000-000000000024', 'c0000005-0000-0000-0000-000000000005', '2026-05-27', 'ABSENT',  'b0000007-0000-0000-0000-000000000007', NOW(), NOW()),
    (gen_random_uuid(), '926c372c-139d-460d-83b1-1a80ef92db57', 'd0000025-0000-0000-0000-000000000025', 'c0000005-0000-0000-0000-000000000005', '2026-05-27', 'PRESENT', 'b0000007-0000-0000-0000-000000000007', NOW(), NOW()),
    -- Class 5A
    (gen_random_uuid(), '926c372c-139d-460d-83b1-1a80ef92db57', 'd0000026-0000-0000-0000-000000000026', 'c0000006-0000-0000-0000-000000000006', '2026-05-27', 'PRESENT', 'e38bd555-29e6-4ab1-a725-f4d2687806ee', NOW(), NOW()),
    (gen_random_uuid(), '926c372c-139d-460d-83b1-1a80ef92db57', 'd0000027-0000-0000-0000-000000000027', 'c0000006-0000-0000-0000-000000000006', '2026-05-27', 'PRESENT', 'e38bd555-29e6-4ab1-a725-f4d2687806ee', NOW(), NOW()),
    (gen_random_uuid(), '926c372c-139d-460d-83b1-1a80ef92db57', 'd0000028-0000-0000-0000-000000000028', 'c0000006-0000-0000-0000-000000000006', '2026-05-27', 'LATE',    'e38bd555-29e6-4ab1-a725-f4d2687806ee', NOW(), NOW()),
    -- Class 5B
    (gen_random_uuid(), '926c372c-139d-460d-83b1-1a80ef92db57', 'd0000029-0000-0000-0000-000000000029', 'c0000007-0000-0000-0000-000000000007', '2026-05-27', 'PRESENT', 'b0000001-0000-0000-0000-000000000001', NOW(), NOW()),
    (gen_random_uuid(), '926c372c-139d-460d-83b1-1a80ef92db57', 'd0000030-0000-0000-0000-000000000030', 'c0000007-0000-0000-0000-000000000007', '2026-05-27', 'PRESENT', 'b0000001-0000-0000-0000-000000000001', NOW(), NOW()),
    (gen_random_uuid(), '926c372c-139d-460d-83b1-1a80ef92db57', 'd0000031-0000-0000-0000-000000000031', 'c0000007-0000-0000-0000-000000000007', '2026-05-27', 'PRESENT', 'b0000001-0000-0000-0000-000000000001', NOW(), NOW()),
    -- Class 6A
    (gen_random_uuid(), '926c372c-139d-460d-83b1-1a80ef92db57', 'd0000032-0000-0000-0000-000000000032', 'c0000008-0000-0000-0000-000000000008', '2026-05-27', 'PRESENT', 'b0000002-0000-0000-0000-000000000002', NOW(), NOW()),
    (gen_random_uuid(), '926c372c-139d-460d-83b1-1a80ef92db57', 'd0000033-0000-0000-0000-000000000033', 'c0000008-0000-0000-0000-000000000008', '2026-05-27', 'PRESENT', 'b0000002-0000-0000-0000-000000000002', NOW(), NOW()),
    (gen_random_uuid(), '926c372c-139d-460d-83b1-1a80ef92db57', 'd0000034-0000-0000-0000-000000000034', 'c0000008-0000-0000-0000-000000000008', '2026-05-27', 'PRESENT', 'b0000002-0000-0000-0000-000000000002', NOW(), NOW()),
    -- Class 6B
    (gen_random_uuid(), '926c372c-139d-460d-83b1-1a80ef92db57', 'd0000035-0000-0000-0000-000000000035', 'c0000009-0000-0000-0000-000000000009', '2026-05-27', 'PRESENT', 'b0000003-0000-0000-0000-000000000003', NOW(), NOW()),
    (gen_random_uuid(), '926c372c-139d-460d-83b1-1a80ef92db57', 'd0000036-0000-0000-0000-000000000036', 'c0000009-0000-0000-0000-000000000009', '2026-05-27', 'PRESENT', 'b0000003-0000-0000-0000-000000000003', NOW(), NOW()),
    (gen_random_uuid(), '926c372c-139d-460d-83b1-1a80ef92db57', 'd0000037-0000-0000-0000-000000000037', 'c0000009-0000-0000-0000-000000000009', '2026-05-27', 'PRESENT', 'b0000003-0000-0000-0000-000000000003', NOW(), NOW());

-- ============================================================
-- FEATURE OVERRIDE: Enable SUBSTITUTE_TEACHERS for VMS School
-- (GROWTH plan includes it, but add explicit override as safety)
-- ============================================================
INSERT INTO feature_overrides (id, school_id, feature_key, enabled, config, note, created_at, updated_at)
VALUES (
    gen_random_uuid(),
    '926c372c-139d-460d-83b1-1a80ef92db57',
    'SUBSTITUTE_TEACHERS',
    TRUE,
    '{}',
    'Enabled for E2E test seed',
    NOW(),
    NOW()
);

-- ============================================================
-- ADDITIONAL STAFF: ACCOUNTANT + LIBRARIAN
-- password: Teacher@123 → $2b$10$8XFYtBztve0ro4hOvJHa/O/n0CdIPHjjdXQrT2ViwDLFT2G1qCHES
-- ============================================================
INSERT INTO staff (id, school_id, first_name, last_name, email, role, is_active,
                   password_hash, identifier_verified, must_reset_password,
                   failed_login_count, created_at, updated_at)
VALUES
    (
        'b0000010-0000-0000-0000-000000000010',
        '926c372c-139d-460d-83b1-1a80ef92db57',
        'Sunita', 'Rao', 'accountant@vms.school', 'ACCOUNTANT', TRUE,
        '$2b$10$8XFYtBztve0ro4hOvJHa/O/n0CdIPHjjdXQrT2ViwDLFT2G1qCHES',
        TRUE, FALSE, 0, NOW(), NOW()
    ),
    (
        'b0000011-0000-0000-0000-000000000011',
        '926c372c-139d-460d-83b1-1a80ef92db57',
        'Vikram', 'Das', 'librarian@vms.school', 'LIBRARIAN', TRUE,
        '$2b$10$8XFYtBztve0ro4hOvJHa/O/n0CdIPHjjdXQrT2ViwDLFT2G1qCHES',
        TRUE, FALSE, 0, NOW(), NOW()
    );

-- ============================================================
-- LIBRARY BOOKS (pre-seeded for library E2E tests)
-- ============================================================
INSERT INTO library_books (id, school_id, title, author, isbn, category, total_copies, available_copies, is_active, created_at, updated_at)
VALUES
    ('e0000001-0000-0000-0000-000000000001', '926c372c-139d-460d-83b1-1a80ef92db57',
     'The Jungle Book', 'Rudyard Kipling', '978-0-14-303943-3', 'Fiction', 5, 5, TRUE, NOW(), NOW()),
    ('e0000002-0000-0000-0000-000000000002', '926c372c-139d-460d-83b1-1a80ef92db57',
     'Wings of Fire', 'A.P.J. Abdul Kalam', '978-81-7371-146-1', 'Biography', 3, 2, TRUE, NOW(), NOW()),
    ('e0000003-0000-0000-0000-000000000003', '926c372c-139d-460d-83b1-1a80ef92db57',
     'Malgudi Days', 'R.K. Narayan', '978-0-14-303955-6', 'Fiction', 4, 4, TRUE, NOW(), NOW());

-- One active issue for Wings of Fire (so available_copies=2, not 3)
INSERT INTO library_issues (id, school_id, book_id, student_id, issued_at, due_date, created_at, updated_at)
VALUES (
    'f0000001-0000-0000-0000-000000000001',
    '926c372c-139d-460d-83b1-1a80ef92db57',
    'e0000002-0000-0000-0000-000000000002',
    'd0000001-0000-0000-0000-000000000001',  -- Aarav Sharma
    NOW() - INTERVAL '5 days',
    CURRENT_DATE + INTERVAL '9 days',
    NOW(), NOW()
);

-- ============================================================
-- FEATURE OVERRIDES: Library + PTM enabled for VMS School
-- ============================================================
INSERT INTO feature_overrides (id, school_id, feature_key, enabled, config, note, created_at, updated_at)
VALUES
    (gen_random_uuid(), '926c372c-139d-460d-83b1-1a80ef92db57', 'LIBRARY',         TRUE, '{}', 'E2E seed', NOW(), NOW()),
    (gen_random_uuid(), '926c372c-139d-460d-83b1-1a80ef92db57', 'PTM_SCHEDULING',  TRUE, '{}', 'E2E seed', NOW(), NOW()),
    (gen_random_uuid(), '926c372c-139d-460d-83b1-1a80ef92db57', 'STAFF_ATTENDANCE',TRUE, '{}', 'E2E seed', NOW(), NOW()),
    (gen_random_uuid(), '926c372c-139d-460d-83b1-1a80ef92db57', 'CIRCULARS',       TRUE, '{}', 'E2E seed', NOW(), NOW())
ON CONFLICT DO NOTHING;

