-- Assign class teachers to sections
-- Teachers:
-- Ananya Singh    -> Class 1.A
-- Rohan Mehta     -> Class 1.B
-- Deepa Nair      -> Class 2.A
-- Suresh Patel    -> Class 2.B
-- Meera Iyer      -> Class 3.A
-- Arjun Kumar     -> Class 3.B
-- Lakshmi Devi    -> Class 4.A
-- Priya Sharma    -> Class 4.B (was on Class 6.A - keep her there too)
-- Classes 5 and 6 get the same teachers rotated

-- Class 1.A -> Ananya Singh
UPDATE sections SET class_teacher_id = (SELECT id FROM staff WHERE email='ananya.singh@vms.school')
WHERE id = '032ec61c-3373-4389-9169-16ae826c357a';

-- Class 1.B -> Rohan Mehta
UPDATE sections SET class_teacher_id = (SELECT id FROM staff WHERE email='rohan.mehta@vms.school')
WHERE id = 'c76ef353-16d9-4d5d-9785-0b3d74e0e641';

-- Class 2.A -> Deepa Nair
UPDATE sections SET class_teacher_id = (SELECT id FROM staff WHERE email='deepa.nair@vms.school')
WHERE id = '64a5ca42-7480-46d2-83c1-9bd44a12dcb7';

-- Class 2.B -> Suresh Patel
UPDATE sections SET class_teacher_id = (SELECT id FROM staff WHERE email='suresh.patel@vms.school')
WHERE id = '8132caf5-0eff-4ed0-a9c8-2dfd501396be';

-- Class 3.A -> Meera Iyer
UPDATE sections SET class_teacher_id = (SELECT id FROM staff WHERE email='meera.iyer@vms.school')
WHERE id = '42507005-df2c-434e-9e9c-706254aa938c';

-- Class 3.B -> Arjun Kumar
UPDATE sections SET class_teacher_id = (SELECT id FROM staff WHERE email='arjun.kumar@vms.school')
WHERE id = '52bddd2c-a6f0-406f-8faf-2af17c349965';

-- Class 4.A -> Lakshmi Devi
UPDATE sections SET class_teacher_id = (SELECT id FROM staff WHERE email='lakshmi.devi@vms.school')
WHERE id = '5eed4526-e17f-45d5-b145-07a444ceb909';

-- Class 4.B -> Priya Sharma
UPDATE sections SET class_teacher_id = (SELECT id FROM staff WHERE email='priya.sharma@vms.school')
WHERE id = '936669f4-0df0-432d-9f3e-18f2a3b51a59';

-- Class 5.A -> Ananya Singh (double duty)
UPDATE sections SET class_teacher_id = (SELECT id FROM staff WHERE email='ananya.singh@vms.school')
WHERE id = 'c1534e5c-c4e3-4ecf-bc37-adf00954a69a';

-- Class 5.B -> Rohan Mehta (double duty)
UPDATE sections SET class_teacher_id = (SELECT id FROM staff WHERE email='rohan.mehta@vms.school')
WHERE id = '145c23e6-3725-4b71-a7d4-08604d2336c3';

-- Class 6.A -> Priya Sharma (already assigned from E2E test)
UPDATE sections SET class_teacher_id = (SELECT id FROM staff WHERE email='priya.sharma@vms.school')
WHERE id = 'd67338d7-1375-4fee-82cf-2da22967f237';

-- Class 6.B -> Meera Iyer
UPDATE sections SET class_teacher_id = (SELECT id FROM staff WHERE email='meera.iyer@vms.school')
WHERE id = '1786f17f-6f20-4e73-824b-0dbd1bb6c7fa';

-- Verify
SELECT c.name||'.'||s.name as section, st.first_name||' '||st.last_name as teacher
FROM sections s
JOIN school_classes c ON c.id = s.class_id
LEFT JOIN staff st ON st.id = s.class_teacher_id
WHERE s.school_id = '926c372c-139d-460d-83b1-1a80ef92db57'
  AND c.name LIKE 'Class %'
ORDER BY c.name, s.name;
