-- Bulk-create 3-4 students per section (Class 1-6, sections A/B)
-- Then create timetable entries for Mon-Fri across all sections

-- ============ STUDENTS ============
-- Class 1.A (032ec61c-3373-4389-9169-16ae826c357a)
INSERT INTO students (id, school_id, first_name, last_name, date_of_birth, gender, created_at)
VALUES
  (gen_random_uuid(),'926c372c-139d-460d-83b1-1a80ef92db57','Aarav','Sharma','2018-06-15','MALE',now()),
  (gen_random_uuid(),'926c372c-139d-460d-83b1-1a80ef92db57','Diya','Patel','2018-09-22','FEMALE',now()),
  (gen_random_uuid(),'926c372c-139d-460d-83b1-1a80ef92db57','Ishaan','Verma','2018-03-10','MALE',now()),
  (gen_random_uuid(),'926c372c-139d-460d-83b1-1a80ef92db57','Kavya','Nair','2018-11-05','FEMALE',now());

-- Enroll in Class 1.A
INSERT INTO student_enrollments (id, student_id, section_id, academic_year_id, roll_number, enrollment_date, status, created_at)
SELECT gen_random_uuid(), s.id, '032ec61c-3373-4389-9169-16ae826c357a', '8fd977b1-e495-42a5-8ef9-473aabd57ff5',
       ROW_NUMBER() OVER (ORDER BY s.first_name)::TEXT, CURRENT_DATE, 'ACTIVE', now()
FROM students s
WHERE s.school_id='926c372c-139d-460d-83b1-1a80ef92db57' AND s.first_name IN ('Aarav','Diya','Ishaan','Kavya')
AND NOT EXISTS (SELECT 1 FROM student_enrollments se WHERE se.student_id=s.id);

-- Class 1.B (c76ef353-16d9-4d5d-9785-0b3d74e0e641)
INSERT INTO students (id, school_id, first_name, last_name, date_of_birth, gender, created_at)
VALUES
  (gen_random_uuid(),'926c372c-139d-460d-83b1-1a80ef92db57','Aryan','Singh','2018-07-20','MALE',now()),
  (gen_random_uuid(),'926c372c-139d-460d-83b1-1a80ef92db57','Priya','Gupta','2018-08-14','FEMALE',now()),
  (gen_random_uuid(),'926c372c-139d-460d-83b1-1a80ef92db57','Rohan','Das','2018-01-30','MALE',now());

INSERT INTO student_enrollments (id, student_id, section_id, academic_year_id, roll_number, enrollment_date, status, created_at)
SELECT gen_random_uuid(), s.id, 'c76ef353-16d9-4d5d-9785-0b3d74e0e641', '8fd977b1-e495-42a5-8ef9-473aabd57ff5',
       ROW_NUMBER() OVER (ORDER BY s.first_name)::TEXT, CURRENT_DATE, 'ACTIVE', now()
FROM students s
WHERE s.school_id='926c372c-139d-460d-83b1-1a80ef92db57' AND s.first_name IN ('Aryan','Priya','Rohan') AND s.last_name IN ('Singh','Gupta','Das')
AND NOT EXISTS (SELECT 1 FROM student_enrollments se WHERE se.student_id=s.id);

-- Class 2.A (64a5ca42-7480-46d2-83c1-9bd44a12dcb7)
INSERT INTO students (id, school_id, first_name, last_name, date_of_birth, gender, created_at)
VALUES
  (gen_random_uuid(),'926c372c-139d-460d-83b1-1a80ef92db57','Ansh','Mehta','2017-05-11','MALE',now()),
  (gen_random_uuid(),'926c372c-139d-460d-83b1-1a80ef92db57','Riya','Shah','2017-08-25','FEMALE',now()),
  (gen_random_uuid(),'926c372c-139d-460d-83b1-1a80ef92db57','Vivaan','Joshi','2017-02-18','MALE',now());

INSERT INTO student_enrollments (id, student_id, section_id, academic_year_id, roll_number, enrollment_date, status, created_at)
SELECT gen_random_uuid(), s.id, '64a5ca42-7480-46d2-83c1-9bd44a12dcb7', '8fd977b1-e495-42a5-8ef9-473aabd57ff5',
       ROW_NUMBER() OVER (ORDER BY s.first_name)::TEXT, CURRENT_DATE, 'ACTIVE', now()
FROM students s
WHERE s.school_id='926c372c-139d-460d-83b1-1a80ef92db57' AND s.first_name IN ('Ansh','Riya','Vivaan')
AND NOT EXISTS (SELECT 1 FROM student_enrollments se WHERE se.student_id=s.id);

-- Class 2.B (8132caf5-0eff-4ed0-a9c8-2dfd501396be)
INSERT INTO students (id, school_id, first_name, last_name, date_of_birth, gender, created_at)
VALUES
  (gen_random_uuid(),'926c372c-139d-460d-83b1-1a80ef92db57','Kabir','Rao','2017-10-07','MALE',now()),
  (gen_random_uuid(),'926c372c-139d-460d-83b1-1a80ef92db57','Tanvi','Pillai','2017-12-15','FEMALE',now()),
  (gen_random_uuid(),'926c372c-139d-460d-83b1-1a80ef92db57','Yash','Kumar','2017-04-03','MALE',now());

INSERT INTO student_enrollments (id, student_id, section_id, academic_year_id, roll_number, enrollment_date, status, created_at)
SELECT gen_random_uuid(), s.id, '8132caf5-0eff-4ed0-a9c8-2dfd501396be', '8fd977b1-e495-42a5-8ef9-473aabd57ff5',
       ROW_NUMBER() OVER (ORDER BY s.first_name)::TEXT, CURRENT_DATE, 'ACTIVE', now()
FROM students s
WHERE s.school_id='926c372c-139d-460d-83b1-1a80ef92db57' AND s.first_name IN ('Kabir','Tanvi','Yash') AND s.last_name IN ('Rao','Pillai','Kumar')
AND NOT EXISTS (SELECT 1 FROM student_enrollments se WHERE se.student_id=s.id);

-- Class 3.A (42507005-df2c-434e-9e9c-706254aa938c)
INSERT INTO students (id, school_id, first_name, last_name, date_of_birth, gender, created_at)
VALUES
  (gen_random_uuid(),'926c372c-139d-460d-83b1-1a80ef92db57','Aditi','Sharma','2016-03-22','FEMALE',now()),
  (gen_random_uuid(),'926c372c-139d-460d-83b1-1a80ef92db57','Devansh','Tiwari','2016-07-10','MALE',now()),
  (gen_random_uuid(),'926c372c-139d-460d-83b1-1a80ef92db57','Pooja','Mishra','2016-11-30','FEMALE',now());

INSERT INTO student_enrollments (id, student_id, section_id, academic_year_id, roll_number, enrollment_date, status, created_at)
SELECT gen_random_uuid(), s.id, '42507005-df2c-434e-9e9c-706254aa938c', '8fd977b1-e495-42a5-8ef9-473aabd57ff5',
       ROW_NUMBER() OVER (ORDER BY s.first_name)::TEXT, CURRENT_DATE, 'ACTIVE', now()
FROM students s
WHERE s.school_id='926c372c-139d-460d-83b1-1a80ef92db57' AND s.first_name IN ('Aditi','Devansh','Pooja') AND s.last_name IN ('Sharma','Tiwari','Mishra')
AND NOT EXISTS (SELECT 1 FROM student_enrollments se WHERE se.student_id=s.id);

-- Class 3.B (52bddd2c-a6f0-406f-8faf-2af17c349965)
INSERT INTO students (id, school_id, first_name, last_name, date_of_birth, gender, created_at)
VALUES
  (gen_random_uuid(),'926c372c-139d-460d-83b1-1a80ef92db57','Chirag','Bose','2016-06-18','MALE',now()),
  (gen_random_uuid(),'926c372c-139d-460d-83b1-1a80ef92db57','Neha','Roy','2016-09-05','FEMALE',now()),
  (gen_random_uuid(),'926c372c-139d-460d-83b1-1a80ef92db57','Shiv','Pandey','2016-01-25','MALE',now());

INSERT INTO student_enrollments (id, student_id, section_id, academic_year_id, roll_number, enrollment_date, status, created_at)
SELECT gen_random_uuid(), s.id, '52bddd2c-a6f0-406f-8faf-2af17c349965', '8fd977b1-e495-42a5-8ef9-473aabd57ff5',
       ROW_NUMBER() OVER (ORDER BY s.first_name)::TEXT, CURRENT_DATE, 'ACTIVE', now()
FROM students s
WHERE s.school_id='926c372c-139d-460d-83b1-1a80ef92db57' AND s.first_name IN ('Chirag','Neha','Shiv') AND s.last_name IN ('Bose','Roy','Pandey')
AND NOT EXISTS (SELECT 1 FROM student_enrollments se WHERE se.student_id=s.id);

-- Class 4.A (5eed4526-e17f-45d5-b145-07a444ceb909)
INSERT INTO students (id, school_id, first_name, last_name, date_of_birth, gender, created_at)
VALUES
  (gen_random_uuid(),'926c372c-139d-460d-83b1-1a80ef92db57','Aditya','Nair','2015-04-12','MALE',now()),
  (gen_random_uuid(),'926c372c-139d-460d-83b1-1a80ef92db57','Sakshi','Iyer','2015-08-20','FEMALE',now()),
  (gen_random_uuid(),'926c372c-139d-460d-83b1-1a80ef92db57','Vedant','Jain','2015-12-05','MALE',now());

INSERT INTO student_enrollments (id, student_id, section_id, academic_year_id, roll_number, enrollment_date, status, created_at)
SELECT gen_random_uuid(), s.id, '5eed4526-e17f-45d5-b145-07a444ceb909', '8fd977b1-e495-42a5-8ef9-473aabd57ff5',
       ROW_NUMBER() OVER (ORDER BY s.first_name)::TEXT, CURRENT_DATE, 'ACTIVE', now()
FROM students s
WHERE s.school_id='926c372c-139d-460d-83b1-1a80ef92db57' AND s.first_name IN ('Aditya','Sakshi','Vedant') AND s.last_name IN ('Nair','Iyer','Jain')
AND NOT EXISTS (SELECT 1 FROM student_enrollments se WHERE se.student_id=s.id);

-- Class 4.B (936669f4-0df0-432d-9f3e-18f2a3b51a59)
INSERT INTO students (id, school_id, first_name, last_name, date_of_birth, gender, created_at)
VALUES
  (gen_random_uuid(),'926c372c-139d-460d-83b1-1a80ef92db57','Harsh','Gupta','2015-02-28','MALE',now()),
  (gen_random_uuid(),'926c372c-139d-460d-83b1-1a80ef92db57','Mansi','Bhatt','2015-07-14','FEMALE',now()),
  (gen_random_uuid(),'926c372c-139d-460d-83b1-1a80ef92db57','Parth','Desai','2015-10-22','MALE',now());

INSERT INTO student_enrollments (id, student_id, section_id, academic_year_id, roll_number, enrollment_date, status, created_at)
SELECT gen_random_uuid(), s.id, '936669f4-0df0-432d-9f3e-18f2a3b51a59', '8fd977b1-e495-42a5-8ef9-473aabd57ff5',
       ROW_NUMBER() OVER (ORDER BY s.first_name)::TEXT, CURRENT_DATE, 'ACTIVE', now()
FROM students s
WHERE s.school_id='926c372c-139d-460d-83b1-1a80ef92db57' AND s.first_name IN ('Harsh','Mansi','Parth') AND s.last_name IN ('Gupta','Bhatt','Desai')
AND NOT EXISTS (SELECT 1 FROM student_enrollments se WHERE se.student_id=s.id);

-- Class 5.A (c1534e5c-c4e3-4ecf-bc37-adf00954a69a)
INSERT INTO students (id, school_id, first_name, last_name, date_of_birth, gender, created_at)
VALUES
  (gen_random_uuid(),'926c372c-139d-460d-83b1-1a80ef92db57','Arnav','Kapoor','2014-05-08','MALE',now()),
  (gen_random_uuid(),'926c372c-139d-460d-83b1-1a80ef92db57','Anjali','Dubey','2014-09-17','FEMALE',now()),
  (gen_random_uuid(),'926c372c-139d-460d-83b1-1a80ef92db57','Kartik','Reddy','2014-11-30','MALE',now());

INSERT INTO student_enrollments (id, student_id, section_id, academic_year_id, roll_number, enrollment_date, status, created_at)
SELECT gen_random_uuid(), s.id, 'c1534e5c-c4e3-4ecf-bc37-adf00954a69a', '8fd977b1-e495-42a5-8ef9-473aabd57ff5',
       ROW_NUMBER() OVER (ORDER BY s.first_name)::TEXT, CURRENT_DATE, 'ACTIVE', now()
FROM students s
WHERE s.school_id='926c372c-139d-460d-83b1-1a80ef92db57' AND s.first_name IN ('Arnav','Anjali','Kartik') AND s.last_name IN ('Kapoor','Dubey','Reddy')
AND NOT EXISTS (SELECT 1 FROM student_enrollments se WHERE se.student_id=s.id);

-- Class 5.B (145c23e6-3725-4b71-a7d4-08604d2336c3)
INSERT INTO students (id, school_id, first_name, last_name, date_of_birth, gender, created_at)
VALUES
  (gen_random_uuid(),'926c372c-139d-460d-83b1-1a80ef92db57','Bhavya','Choudhury','2014-03-12','FEMALE',now()),
  (gen_random_uuid(),'926c372c-139d-460d-83b1-1a80ef92db57','Siddharth','Banerjee','2014-07-25','MALE',now()),
  (gen_random_uuid(),'926c372c-139d-460d-83b1-1a80ef92db57','Tanya','Singh','2014-10-08','FEMALE',now());

INSERT INTO student_enrollments (id, student_id, section_id, academic_year_id, roll_number, enrollment_date, status, created_at)
SELECT gen_random_uuid(), s.id, '145c23e6-3725-4b71-a7d4-08604d2336c3', '8fd977b1-e495-42a5-8ef9-473aabd57ff5',
       ROW_NUMBER() OVER (ORDER BY s.first_name)::TEXT, CURRENT_DATE, 'ACTIVE', now()
FROM students s
WHERE s.school_id='926c372c-139d-460d-83b1-1a80ef92db57' AND s.first_name IN ('Bhavya','Siddharth','Tanya') AND s.last_name IN ('Choudhury','Banerjee','Singh')
AND NOT EXISTS (SELECT 1 FROM student_enrollments se WHERE se.student_id=s.id);

-- Class 6.B (1786f17f-6f20-4e73-824b-0dbd1bb6c7fa) - Class 6.A already has Rahul Verma
INSERT INTO students (id, school_id, first_name, last_name, date_of_birth, gender, created_at)
VALUES
  (gen_random_uuid(),'926c372c-139d-460d-83b1-1a80ef92db57','Dhruv','Agarwal','2013-06-20','MALE',now()),
  (gen_random_uuid(),'926c372c-139d-460d-83b1-1a80ef92db57','Sanya','Malhotra','2013-09-15','FEMALE',now()),
  (gen_random_uuid(),'926c372c-139d-460d-83b1-1a80ef92db57','Viraj','Khanna','2013-12-01','MALE',now());

INSERT INTO student_enrollments (id, student_id, section_id, academic_year_id, roll_number, enrollment_date, status, created_at)
SELECT gen_random_uuid(), s.id, '1786f17f-6f20-4e73-824b-0dbd1bb6c7fa', '8fd977b1-e495-42a5-8ef9-473aabd57ff5',
       ROW_NUMBER() OVER (ORDER BY s.first_name)::TEXT, CURRENT_DATE, 'ACTIVE', now()
FROM students s
WHERE s.school_id='926c372c-139d-460d-83b1-1a80ef92db57' AND s.first_name IN ('Dhruv','Sanya','Viraj') AND s.last_name IN ('Agarwal','Malhotra','Khanna')
AND NOT EXISTS (SELECT 1 FROM student_enrollments se WHERE se.student_id=s.id);

-- Verify student count per section
SELECT c.name||'.'||s.name as section, COUNT(se.id) as student_count, st.first_name||' '||st.last_name as class_teacher
FROM sections s
JOIN school_classes c ON c.id=s.class_id
LEFT JOIN student_enrollments se ON se.section_id=s.id AND se.status='ACTIVE'
LEFT JOIN staff st ON st.id=s.class_teacher_id
WHERE s.school_id='926c372c-139d-460d-83b1-1a80ef92db57' AND c.name LIKE 'Class %'
GROUP BY c.name, s.name, st.first_name, st.last_name
ORDER BY c.name, s.name;
