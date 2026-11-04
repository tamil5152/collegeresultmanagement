-- =====================================================================
-- DEVELOPMENT / DEMO DATA ONLY  -  DO NOT LOAD INTO A PRODUCTION DATABASE
-- All people, e-mail addresses (example.test) and marks are fictitious.
-- The single shared password for every seeded account is:  Password@123
-- The grading rule below is a SAMPLE PLACEHOLDER, not an official regulation.
-- Load into an EMPTY database after schema.sql:
--   mysql -u <user> -p <database> < database/seed.sql
-- =====================================================================

SET @pw = '$2a$10$GNGILeU8Ryv3QM4YjgRzzeecG5AfXVKfRshGGMewKYw93xW8qADz6';  -- BCrypt of Password@123

INSERT INTO users (id, username, email, password_hash, full_name, role) VALUES
 (1,  'admin',      'admin@example.test',      @pw, 'Dev Administrator',   'ADMIN'),
 (2,  'fac.cse1',   'anita.rao@example.test', @pw, 'Dr. Anita Rao',       'FACULTY'),
 (3,  'fac.cse2',   'ravi.kumar@example.test',@pw, 'Prof. Ravi Kumar',    'FACULTY'),
 (4,  'fac.ece1',   'meena.iyer@example.test',@pw, 'Dr. Meena Iyer',      'FACULTY'),
 (5,  'cs2024001',  'aarav.sharma@example.test', @pw, 'Aarav Sharma',     'STUDENT'),
 (6,  'cs2024002',  'diya.patel@example.test',   @pw, 'Diya Patel',       'STUDENT'),
 (7,  'cs2024003',  'ishaan.verma@example.test', @pw, 'Ishaan Verma',     'STUDENT'),
 (8,  'cs2024004',  'kavya.nair@example.test',   @pw, 'Kavya Nair',       'STUDENT'),
 (9,  'cs2024005',  'rohan.mehta@example.test',  @pw, 'Rohan Mehta',      'STUDENT'),
 (10, 'cs2024006',  'sneha.reddy@example.test',  @pw, 'Sneha Reddy',      'STUDENT'),
 (11, 'ec2024001',  'arjun.singh@example.test',  @pw, 'Arjun Singh',      'STUDENT'),
 (12, 'ec2024002',  'priya.menon@example.test',  @pw, 'Priya Menon',      'STUDENT'),
 (13, 'ec2024003',  'vikram.joshi@example.test', @pw, 'Vikram Joshi',     'STUDENT'),
 (14, 'ec2024004',  'neha.gupta@example.test',   @pw, 'Neha Gupta',       'STUDENT');

INSERT INTO departments (id, code, name) VALUES
 (1, 'CSE', 'Computer Science and Engineering'),
 (2, 'ECE', 'Electronics and Communication Engineering');

INSERT INTO academic_years (id, label, start_date, end_date) VALUES
 (1, '2024-25', '2024-07-01', '2025-06-30'),
 (2, '2025-26', '2025-07-01', '2026-06-30');

INSERT INTO semesters (id, academic_year_id, semester_number, name, start_date, end_date) VALUES
 (1, 1, 1, 'Semester 1', '2024-07-15', '2024-12-20'),
 (2, 1, 2, 'Semester 2', '2025-01-06', '2025-05-30'),
 (3, 2, 3, 'Semester 3', '2025-07-14', '2025-12-19');

INSERT INTO examination_sessions (id, name, semester_id, start_date, end_date, status) VALUES
 (1, 'End Semester Examination - Dec 2024', 1, '2024-12-02', '2024-12-18', 'CLOSED'),
 (2, 'End Semester Examination - May 2025', 2, '2025-05-12', '2025-05-28', 'CLOSED'),
 (3, 'End Semester Examination - Dec 2025', 3, '2025-12-01', '2025-12-17', 'OPEN');

-- (department, code, name, sem, credits, int max, ext max, int pass, ext pass, total pass)
INSERT INTO subjects (id, department_id, code, name, semester_number, credits, internal_max, external_max,
                      internal_pass, external_pass, total_pass) VALUES
 (1,  1, 'CS101', 'Programming Fundamentals',    1, 4.00, 40, 60, 16, 24, 40),
 (2,  1, 'CS102', 'Engineering Mathematics I',   1, 4.00, 40, 60, 16, 24, 40),
 (3,  1, 'CS103', 'Digital Logic Design',        1, 3.00, 40, 60, 16, 24, 40),
 (4,  1, 'CS201', 'Data Structures',             2, 4.00, 40, 60, 16, 24, 40),
 (5,  1, 'CS202', 'Discrete Mathematics',        2, 3.00, 40, 60, 16, 24, 40),
 (6,  1, 'CS203', 'Computer Organization',       2, 3.00, 40, 60, 16, 24, 40),
 (7,  1, 'CS301', 'Design and Analysis of Algorithms', 3, 4.00, 40, 60, 16, 24, 40),
 (8,  1, 'CS302', 'Database Management Systems', 3, 4.00, 40, 60, 16, 24, 40),
 (9,  1, 'CS303', 'Operating Systems',           3, 3.00, 40, 60, 16, 24, 40),
 (10, 2, 'EC301', 'Signals and Systems',         3, 4.00, 40, 60, 16, 24, 40),
 (11, 2, 'EC302', 'Analog Circuits',             3, 4.00, 40, 60, 16, 24, 40);

INSERT INTO faculty (id, user_id, department_id, employee_code, designation, phone) VALUES
 (1, 2, 1, 'EMP001', 'Associate Professor', '+910000000001'),
 (2, 3, 1, 'EMP002', 'Professor',           '+910000000002'),
 (3, 4, 2, 'EMP003', 'Associate Professor', '+910000000003');

INSERT INTO students (id, user_id, department_id, roll_number, batch_year, current_semester, phone, date_of_birth) VALUES
 (1,  5, 1, 'CS2024001', 2024, 3, '+910000000101', '2006-03-14'),
 (2,  6, 1, 'CS2024002', 2024, 3, '+910000000102', '2006-07-22'),
 (3,  7, 1, 'CS2024003', 2024, 3, '+910000000103', '2005-11-05'),
 (4,  8, 1, 'CS2024004', 2024, 3, '+910000000104', '2006-01-30'),
 (5,  9, 1, 'CS2024005', 2024, 3, '+910000000105', '2006-09-18'),
 (6, 10, 1, 'CS2024006', 2024, 3, '+910000000106', '2006-05-09'),
 (7, 11, 2, 'EC2024001', 2024, 3, '+910000000107', '2006-02-11'),
 (8, 12, 2, 'EC2024002', 2024, 3, '+910000000108', '2005-12-27'),
 (9, 13, 2, 'EC2024003', 2024, 3, '+910000000109', '2006-04-03'),
 (10,14, 2, 'EC2024004', 2024, 3, '+910000000110', '2006-08-21');

INSERT INTO subject_assignments (subject_id, faculty_id, semester_id) VALUES
 (1, 1, 1), (2, 2, 1), (3, 1, 1),
 (4, 1, 2), (5, 2, 2), (6, 1, 2),
 (7, 1, 3), (8, 2, 3), (9, 1, 3),
 (10, 3, 3), (11, 3, 3);

-- Enrol the six CSE students in their semester 1, 2 and 3 subjects; the ECE students in semester 3.
INSERT INTO student_enrollments (student_id, subject_id, semester_id)
SELECT st.id, sub.id, sem.id
FROM students st
JOIN subjects sub ON sub.department_id = st.department_id
JOIN semesters sem ON sem.semester_number = sub.semester_number
WHERE st.department_id = 1 AND sub.semester_number <= 3
UNION ALL
SELECT st.id, sub.id, sem.id
FROM students st
JOIN subjects sub ON sub.department_id = st.department_id
JOIN semesters sem ON sem.semester_number = sub.semester_number
WHERE st.department_id = 2 AND sub.semester_number = 3;

-- Sample grading rule (PLACEHOLDER - replace with your institution's regulation under Admin > Grading Rules).
INSERT INTO grading_rules (id, name, description, sample_placeholder, include_failed_in_gpa, active) VALUES
 (1, 'Sample 10-point scale (placeholder)',
  'ILLUSTRATIVE VALUES ONLY. Not an official regulation; configure your own boundaries before real use.', 1, 1, 1);

INSERT INTO grading_rule_bands (grading_rule_id, grade, min_percentage, grade_point, fail_grade) VALUES
 (1, 'O',  90.00, 10.00, 0),
 (1, 'A+', 80.00,  9.00, 0),
 (1, 'A',  70.00,  8.00, 0),
 (1, 'B+', 60.00,  7.00, 0),
 (1, 'B',  50.00,  6.00, 0),
 (1, 'C',  45.00,  5.00, 0),
 (1, 'P',  40.00,  4.00, 0),
 (1, 'F',   0.00,  0.00, 1);

-- Semester 1 and 2 marks: already APPROVED, waiting for the admin to publish (Admin > Results Publication).
-- Values are deterministic pseudo-data derived from ids.
INSERT INTO marks (student_id, subject_id, examination_session_id, internal_marks, external_marks, status,
                   entered_by_faculty_id, submitted_at, reviewed_by_user_id, reviewed_at)
SELECT e.student_id, e.subject_id, e.semester_id,
       20 + MOD(e.student_id * 7 + e.subject_id * 3, 21),
       25 + MOD(e.student_id * 11 + e.subject_id * 5, 36),
       'APPROVED', a.faculty_id, NOW(), 1, NOW()
FROM student_enrollments e
JOIN subject_assignments a ON a.subject_id = e.subject_id AND a.semester_id = e.semester_id
WHERE e.semester_id IN (1, 2);

-- One failing subject (external below the pass mark of 24) so that fail handling can be demonstrated.
UPDATE marks SET external_marks = 20.00 WHERE student_id = 6 AND subject_id = 2 AND examination_session_id = 1;

-- Semester 3 (session 3, still OPEN): CS301 fully SUBMITTED for review, CS302 partially DRAFT, rest not started.
INSERT INTO marks (student_id, subject_id, examination_session_id, internal_marks, external_marks, status,
                   entered_by_faculty_id, submitted_at)
SELECT e.student_id, e.subject_id, 3,
       22 + MOD(e.student_id * 5, 19),
       30 + MOD(e.student_id * 9, 31),
       'SUBMITTED', 1, NOW()
FROM student_enrollments e WHERE e.semester_id = 3 AND e.subject_id = 7;

INSERT INTO marks (student_id, subject_id, examination_session_id, internal_marks, external_marks, status,
                   entered_by_faculty_id)
SELECT e.student_id, e.subject_id, 3, 25 + MOD(e.student_id * 3, 14), NULL, 'DRAFT', 2
FROM student_enrollments e WHERE e.semester_id = 3 AND e.subject_id = 8 AND e.student_id <= 3;

INSERT INTO audit_logs (actor_user_id, actor_username, action, entity_type, entity_id, details) VALUES
 (1, 'admin', 'DEV_SEED_LOADED', 'SYSTEM', NULL, '{"note":"Development sample data loaded. Do not use in production."}');
