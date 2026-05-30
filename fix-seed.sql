-- Insert LIBRARIAN staff
INSERT INTO staff (id, school_id, first_name, last_name, email, role, is_active,
                   password_hash, identifier_verified, must_reset_password,
                   failed_login_count, created_at, updated_at)
VALUES (
    'b0000011-0000-0000-0000-000000000011',
    '926c372c-139d-460d-83b1-1a80ef92db57',
    'Vikram', 'Das', 'librarian@vms.school', 'LIBRARIAN', TRUE,
    '$2b$10$8XFYtBztve0ro4hOvJHa/O/n0CdIPHjjdXQrT2ViwDLFT2G1qCHES',
    TRUE, FALSE, 0, NOW(), NOW()
) ON CONFLICT (id) DO NOTHING;

-- Insert ACCOUNTANT staff
INSERT INTO staff (id, school_id, first_name, last_name, email, role, is_active,
                   password_hash, identifier_verified, must_reset_password,
                   failed_login_count, created_at, updated_at)
VALUES (
    'b0000010-0000-0000-0000-000000000010',
    '926c372c-139d-460d-83b1-1a80ef92db57',
    'Sunita', 'Rao', 'accountant@vms.school', 'ACCOUNTANT', TRUE,
    '$2b$10$8XFYtBztve0ro4hOvJHa/O/n0CdIPHjjdXQrT2ViwDLFT2G1qCHES',
    TRUE, FALSE, 0, NOW(), NOW()
) ON CONFLICT (id) DO NOTHING;

-- Insert proper library books (upsert to replace junk data)
INSERT INTO library_books (id, school_id, title, author, isbn, category, total_copies, available_copies, is_active, created_at, updated_at)
VALUES
    ('e0000001-0000-0000-0000-000000000001', '926c372c-139d-460d-83b1-1a80ef92db57', 'The Jungle Book', 'Rudyard Kipling', '978-0-14-303943-3', 'Fiction', 5, 5, TRUE, NOW(), NOW()),
    ('e0000002-0000-0000-0000-000000000002', '926c372c-139d-460d-83b1-1a80ef92db57', 'Wings of Fire', 'A.P.J. Abdul Kalam', '978-81-7371-146-1', 'Biography', 3, 2, TRUE, NOW(), NOW()),
    ('e0000003-0000-0000-0000-000000000003', '926c372c-139d-460d-83b1-1a80ef92db57', 'Malgudi Days', 'R.K. Narayan', '978-0-14-303955-6', 'Fiction', 4, 4, TRUE, NOW(), NOW())
ON CONFLICT (id) DO UPDATE SET
    title = EXCLUDED.title,
    author = EXCLUDED.author,
    isbn = EXCLUDED.isbn,
    category = EXCLUDED.category,
    total_copies = EXCLUDED.total_copies,
    available_copies = EXCLUDED.available_copies,
    updated_at = NOW();

-- Insert library issue for Aarav Sharma (student d0000001)
INSERT INTO library_issues (id, school_id, book_id, student_id, issued_at, due_date, created_at, updated_at)
VALUES (
    'f0000001-0000-0000-0000-000000000001',
    '926c372c-139d-460d-83b1-1a80ef92db57',
    'e0000002-0000-0000-0000-000000000002',
    'd0000001-0000-0000-0000-000000000001',
    NOW() - INTERVAL '5 days',
    CURRENT_DATE + INTERVAL '9 days',
    NOW(), NOW()
) ON CONFLICT (id) DO NOTHING;

-- Feature overrides
INSERT INTO feature_overrides (id, school_id, feature_key, enabled, config, note, created_at, updated_at)
VALUES
    (gen_random_uuid(), '926c372c-139d-460d-83b1-1a80ef92db57', 'LIBRARY', TRUE, '{}', 'E2E seed', NOW(), NOW()),
    (gen_random_uuid(), '926c372c-139d-460d-83b1-1a80ef92db57', 'PTM_SCHEDULING', TRUE, '{}', 'E2E seed', NOW(), NOW()),
    (gen_random_uuid(), '926c372c-139d-460d-83b1-1a80ef92db57', 'STAFF_ATTENDANCE', TRUE, '{}', 'E2E seed', NOW(), NOW()),
    (gen_random_uuid(), '926c372c-139d-460d-83b1-1a80ef92db57', 'CIRCULARS', TRUE, '{}', 'E2E seed', NOW(), NOW())
ON CONFLICT DO NOTHING;

SELECT email, role FROM staff WHERE email IN ('librarian@vms.school','accountant@vms.school');
SELECT title FROM library_books WHERE id IN ('e0000001-0000-0000-0000-000000000001','e0000002-0000-0000-0000-000000000002','e0000003-0000-0000-0000-000000000003');
SELECT feature_key, enabled FROM feature_overrides ORDER BY feature_key;
