-- Bulk-enroll all students (already created) into their sections
-- Students were already inserted (INSERT succeeded), just need enrollments

-- Enroll students into sections based on last_name patterns
-- Class 1.A (section id: 032ec61c-3373-4389-9169-16ae826c357a)
INSERT INTO student_enrollments (student_id, school_id, academic_year_id, section_id, roll_number, status)
SELECT s.id, '926c372c-139d-460d-83b1-1a80ef92db57', '8fd977b1-e495-42a5-8ef9-473aabd57ff5',
       '032ec61c-3373-4389-9169-16ae826c357a',
       ROW_NUMBER() OVER (ORDER BY s.first_name)::INTEGER, 'ACTIVE'
FROM students s
WHERE s.school_id='926c372c-139d-460d-83b1-1a80ef92db57'
  AND s.last_name IN ('Sharma','Patel','Verma','Nair')
  AND s.first_name IN ('Aarav','Diya','Ishaan','Kavya')
  AND NOT EXISTS (SELECT 1 FROM student_enrollments se WHERE se.student_id=s.id AND se.academic_year_id='8fd977b1-e495-42a5-8ef9-473aabd57ff5');

-- Class 1.B (section id: c76ef353-16d9-4d5d-9785-0b3d74e0e641)
INSERT INTO student_enrollments (student_id, school_id, academic_year_id, section_id, roll_number, status)
SELECT s.id, '926c372c-139d-460d-83b1-1a80ef92db57', '8fd977b1-e495-42a5-8ef9-473aabd57ff5',
       'c76ef353-16d9-4d5d-9785-0b3d74e0e641',
       ROW_NUMBER() OVER (ORDER BY s.first_name)::INTEGER, 'ACTIVE'
FROM students s
WHERE s.school_id='926c372c-139d-460d-83b1-1a80ef92db57'
  AND s.last_name IN ('Singh','Gupta','Das') AND s.first_name IN ('Aryan','Priya','Rohan')
  AND NOT EXISTS (SELECT 1 FROM student_enrollments se WHERE se.student_id=s.id AND se.academic_year_id='8fd977b1-e495-42a5-8ef9-473aabd57ff5');

-- Class 2.A
INSERT INTO student_enrollments (student_id, school_id, academic_year_id, section_id, roll_number, status)
SELECT s.id, '926c372c-139d-460d-83b1-1a80ef92db57', '8fd977b1-e495-42a5-8ef9-473aabd57ff5',
       '64a5ca42-7480-46d2-83c1-9bd44a12dcb7',
       ROW_NUMBER() OVER (ORDER BY s.first_name)::INTEGER, 'ACTIVE'
FROM students s
WHERE s.school_id='926c372c-139d-460d-83b1-1a80ef92db57'
  AND s.last_name IN ('Mehta','Shah','Joshi') AND s.first_name IN ('Ansh','Riya','Vivaan')
  AND NOT EXISTS (SELECT 1 FROM student_enrollments se WHERE se.student_id=s.id AND se.academic_year_id='8fd977b1-e495-42a5-8ef9-473aabd57ff5');

-- Class 2.B
INSERT INTO student_enrollments (student_id, school_id, academic_year_id, section_id, roll_number, status)
SELECT s.id, '926c372c-139d-460d-83b1-1a80ef92db57', '8fd977b1-e495-42a5-8ef9-473aabd57ff5',
       '8132caf5-0eff-4ed0-a9c8-2dfd501396be',
       ROW_NUMBER() OVER (ORDER BY s.first_name)::INTEGER, 'ACTIVE'
FROM students s
WHERE s.school_id='926c372c-139d-460d-83b1-1a80ef92db57'
  AND s.last_name IN ('Rao','Pillai','Kumar') AND s.first_name IN ('Kabir','Tanvi','Yash')
  AND NOT EXISTS (SELECT 1 FROM student_enrollments se WHERE se.student_id=s.id AND se.academic_year_id='8fd977b1-e495-42a5-8ef9-473aabd57ff5');

-- Class 3.A
INSERT INTO student_enrollments (student_id, school_id, academic_year_id, section_id, roll_number, status)
SELECT s.id, '926c372c-139d-460d-83b1-1a80ef92db57', '8fd977b1-e495-42a5-8ef9-473aabd57ff5',
       '42507005-df2c-434e-9e9c-706254aa938c',
       ROW_NUMBER() OVER (ORDER BY s.first_name)::INTEGER, 'ACTIVE'
FROM students s
WHERE s.school_id='926c372c-139d-460d-83b1-1a80ef92db57'
  AND s.last_name IN ('Sharma','Tiwari','Mishra') AND s.first_name IN ('Aditi','Devansh','Pooja')
  AND NOT EXISTS (SELECT 1 FROM student_enrollments se WHERE se.student_id=s.id AND se.academic_year_id='8fd977b1-e495-42a5-8ef9-473aabd57ff5');

-- Class 3.B
INSERT INTO student_enrollments (student_id, school_id, academic_year_id, section_id, roll_number, status)
SELECT s.id, '926c372c-139d-460d-83b1-1a80ef92db57', '8fd977b1-e495-42a5-8ef9-473aabd57ff5',
       '52bddd2c-a6f0-406f-8faf-2af17c349965',
       ROW_NUMBER() OVER (ORDER BY s.first_name)::INTEGER, 'ACTIVE'
FROM students s
WHERE s.school_id='926c372c-139d-460d-83b1-1a80ef92db57'
  AND s.last_name IN ('Bose','Roy','Pandey') AND s.first_name IN ('Chirag','Neha','Shiv')
  AND NOT EXISTS (SELECT 1 FROM student_enrollments se WHERE se.student_id=s.id AND se.academic_year_id='8fd977b1-e495-42a5-8ef9-473aabd57ff5');

-- Class 4.A
INSERT INTO student_enrollments (student_id, school_id, academic_year_id, section_id, roll_number, status)
SELECT s.id, '926c372c-139d-460d-83b1-1a80ef92db57', '8fd977b1-e495-42a5-8ef9-473aabd57ff5',
       '5eed4526-e17f-45d5-b145-07a444ceb909',
       ROW_NUMBER() OVER (ORDER BY s.first_name)::INTEGER, 'ACTIVE'
FROM students s
WHERE s.school_id='926c372c-139d-460d-83b1-1a80ef92db57'
  AND s.last_name IN ('Nair','Iyer','Jain') AND s.first_name IN ('Aditya','Sakshi','Vedant')
  AND NOT EXISTS (SELECT 1 FROM student_enrollments se WHERE se.student_id=s.id AND se.academic_year_id='8fd977b1-e495-42a5-8ef9-473aabd57ff5');

-- Class 4.B
INSERT INTO student_enrollments (student_id, school_id, academic_year_id, section_id, roll_number, status)
SELECT s.id, '926c372c-139d-460d-83b1-1a80ef92db57', '8fd977b1-e495-42a5-8ef9-473aabd57ff5',
       '936669f4-0df0-432d-9f3e-18f2a3b51a59',
       ROW_NUMBER() OVER (ORDER BY s.first_name)::INTEGER, 'ACTIVE'
FROM students s
WHERE s.school_id='926c372c-139d-460d-83b1-1a80ef92db57'
  AND s.last_name IN ('Gupta','Bhatt','Desai') AND s.first_name IN ('Harsh','Mansi','Parth')
  AND NOT EXISTS (SELECT 1 FROM student_enrollments se WHERE se.student_id=s.id AND se.academic_year_id='8fd977b1-e495-42a5-8ef9-473aabd57ff5');

-- Class 5.A
INSERT INTO student_enrollments (student_id, school_id, academic_year_id, section_id, roll_number, status)
SELECT s.id, '926c372c-139d-460d-83b1-1a80ef92db57', '8fd977b1-e495-42a5-8ef9-473aabd57ff5',
       'c1534e5c-c4e3-4ecf-bc37-adf00954a69a',
       ROW_NUMBER() OVER (ORDER BY s.first_name)::INTEGER, 'ACTIVE'
FROM students s
WHERE s.school_id='926c372c-139d-460d-83b1-1a80ef92db57'
  AND s.last_name IN ('Kapoor','Dubey','Reddy') AND s.first_name IN ('Arnav','Anjali','Kartik')
  AND NOT EXISTS (SELECT 1 FROM student_enrollments se WHERE se.student_id=s.id AND se.academic_year_id='8fd977b1-e495-42a5-8ef9-473aabd57ff5');

-- Class 5.B
INSERT INTO student_enrollments (student_id, school_id, academic_year_id, section_id, roll_number, status)
SELECT s.id, '926c372c-139d-460d-83b1-1a80ef92db57', '8fd977b1-e495-42a5-8ef9-473aabd57ff5',
       '145c23e6-3725-4b71-a7d4-08604d2336c3',
       ROW_NUMBER() OVER (ORDER BY s.first_name)::INTEGER, 'ACTIVE'
FROM students s
WHERE s.school_id='926c372c-139d-460d-83b1-1a80ef92db57'
  AND s.last_name IN ('Choudhury','Banerjee','Singh') AND s.first_name IN ('Bhavya','Siddharth','Tanya')
  AND NOT EXISTS (SELECT 1 FROM student_enrollments se WHERE se.student_id=s.id AND se.academic_year_id='8fd977b1-e495-42a5-8ef9-473aabd57ff5');

-- Class 6.B
INSERT INTO student_enrollments (student_id, school_id, academic_year_id, section_id, roll_number, status)
SELECT s.id, '926c372c-139d-460d-83b1-1a80ef92db57', '8fd977b1-e495-42a5-8ef9-473aabd57ff5',
       '1786f17f-6f20-4e73-824b-0dbd1bb6c7fa',
       ROW_NUMBER() OVER (ORDER BY s.first_name)::INTEGER, 'ACTIVE'
FROM students s
WHERE s.school_id='926c372c-139d-460d-83b1-1a80ef92db57'
  AND s.last_name IN ('Agarwal','Malhotra','Khanna') AND s.first_name IN ('Dhruv','Sanya','Viraj')
  AND NOT EXISTS (SELECT 1 FROM student_enrollments se WHERE se.student_id=s.id AND se.academic_year_id='8fd977b1-e495-42a5-8ef9-473aabd57ff5');

-- Final summary
SELECT c.name||' '||s.name as section, COUNT(se.id) as students, st.first_name||' '||st.last_name as class_teacher
FROM sections s
JOIN school_classes c ON c.id=s.class_id
LEFT JOIN student_enrollments se ON se.section_id=s.id AND se.status='ACTIVE'
LEFT JOIN staff st ON st.id=s.class_teacher_id
WHERE s.school_id='926c372c-139d-460d-83b1-1a80ef92db57' AND c.name LIKE 'Class %'
GROUP BY c.name, s.name, st.first_name, st.last_name
ORDER BY c.name, s.name;
