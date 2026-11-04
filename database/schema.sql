-- =====================================================================
-- College Result Management System - database schema (MySQL 8.0+)
-- Safe to run on an EMPTY database. It never drops anything.
--   mysql -h <host> -u <user> -p <database> < database/schema.sql
-- Engine InnoDB, charset utf8mb4. All timestamps are stored in UTC.
-- =====================================================================

CREATE TABLE users (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    username        VARCHAR(50)  NOT NULL,
    email           VARCHAR(150) NOT NULL,
    password_hash   VARCHAR(100) NOT NULL,          -- BCrypt hash, never a plain password
    full_name       VARCHAR(150) NOT NULL,
    role            ENUM('ADMIN','FACULTY','STUDENT') NOT NULL,
    active          TINYINT(1)   NOT NULL DEFAULT 1,
    last_login_at   DATETIME     NULL,
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_users_username (username),
    UNIQUE KEY uk_users_email (email),
    KEY idx_users_role_active (role, active)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE departments (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    code        VARCHAR(20)  NOT NULL,
    name        VARCHAR(150) NOT NULL,
    active      TINYINT(1)   NOT NULL DEFAULT 1,
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_departments_code (code),
    UNIQUE KEY uk_departments_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE academic_years (
    id          BIGINT      NOT NULL AUTO_INCREMENT,
    label       VARCHAR(20) NOT NULL,               -- e.g. 2025-26
    start_date  DATE        NOT NULL,
    end_date    DATE        NOT NULL,
    active      TINYINT(1)  NOT NULL DEFAULT 1,
    created_at  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_academic_years_label (label),
    CONSTRAINT chk_academic_years_dates CHECK (end_date > start_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- A semester instance: "semester N running in academic year Y".
CREATE TABLE semesters (
    id               BIGINT       NOT NULL AUTO_INCREMENT,
    academic_year_id BIGINT       NOT NULL,
    semester_number  TINYINT      NOT NULL,
    name             VARCHAR(100) NOT NULL,
    start_date       DATE         NOT NULL,
    end_date         DATE         NOT NULL,
    created_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_semesters_year_number (academic_year_id, semester_number),
    CONSTRAINT fk_semesters_year FOREIGN KEY (academic_year_id) REFERENCES academic_years (id),
    CONSTRAINT chk_semesters_number CHECK (semester_number BETWEEN 1 AND 12),
    CONSTRAINT chk_semesters_dates CHECK (end_date > start_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Subjects belong to a department and a curriculum semester number (1..12).
-- Pass criteria are subject specific (internal / external / total minimums).
CREATE TABLE subjects (
    id                BIGINT        NOT NULL AUTO_INCREMENT,
    department_id     BIGINT        NOT NULL,
    code              VARCHAR(20)   NOT NULL,
    name              VARCHAR(150)  NOT NULL,
    semester_number   TINYINT       NOT NULL,
    credits           DECIMAL(4,2)  NOT NULL,
    internal_max      DECIMAL(6,2)  NOT NULL,
    external_max      DECIMAL(6,2)  NOT NULL,
    internal_pass     DECIMAL(6,2)  NOT NULL DEFAULT 0,
    external_pass     DECIMAL(6,2)  NOT NULL DEFAULT 0,
    total_pass        DECIMAL(6,2)  NOT NULL DEFAULT 0,
    active            TINYINT(1)    NOT NULL DEFAULT 1,
    created_at        DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_subjects_code (code),
    KEY idx_subjects_dept_sem (department_id, semester_number),
    CONSTRAINT fk_subjects_department FOREIGN KEY (department_id) REFERENCES departments (id),
    CONSTRAINT chk_subjects_sem CHECK (semester_number BETWEEN 1 AND 12),
    CONSTRAINT chk_subjects_credits CHECK (credits > 0),
    CONSTRAINT chk_subjects_max CHECK (internal_max >= 0 AND external_max >= 0 AND internal_max + external_max > 0),
    CONSTRAINT chk_subjects_pass CHECK (internal_pass >= 0 AND external_pass >= 0 AND total_pass >= 0
        AND internal_pass <= internal_max AND external_pass <= external_max
        AND total_pass <= internal_max + external_max)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE examination_sessions (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    name        VARCHAR(150) NOT NULL,
    semester_id BIGINT       NOT NULL,
    start_date  DATE         NOT NULL,
    end_date    DATE         NOT NULL,
    status      ENUM('OPEN','CLOSED') NOT NULL DEFAULT 'OPEN',   -- marks can only be entered while OPEN
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_exam_sessions_sem_name (semester_id, name),
    CONSTRAINT fk_exam_sessions_semester FOREIGN KEY (semester_id) REFERENCES semesters (id),
    CONSTRAINT chk_exam_sessions_dates CHECK (end_date >= start_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE students (
    id               BIGINT       NOT NULL AUTO_INCREMENT,
    user_id          BIGINT       NOT NULL,
    department_id    BIGINT       NOT NULL,
    roll_number      VARCHAR(30)  NOT NULL,
    batch_year       SMALLINT     NOT NULL,
    current_semester TINYINT      NOT NULL,
    phone            VARCHAR(20)  NULL,
    date_of_birth    DATE         NULL,
    created_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_students_user (user_id),
    UNIQUE KEY uk_students_roll (roll_number),
    KEY idx_students_dept_sem (department_id, current_semester),
    CONSTRAINT fk_students_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_students_department FOREIGN KEY (department_id) REFERENCES departments (id),
    CONSTRAINT chk_students_sem CHECK (current_semester BETWEEN 1 AND 12)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE faculty (
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    user_id        BIGINT       NOT NULL,
    department_id  BIGINT       NOT NULL,
    employee_code  VARCHAR(30)  NOT NULL,
    designation    VARCHAR(100) NULL,
    phone          VARCHAR(20)  NULL,
    created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_faculty_user (user_id),
    UNIQUE KEY uk_faculty_code (employee_code),
    KEY idx_faculty_dept (department_id),
    CONSTRAINT fk_faculty_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_faculty_department FOREIGN KEY (department_id) REFERENCES departments (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- One faculty member teaches a subject in a semester instance.
CREATE TABLE subject_assignments (
    id          BIGINT   NOT NULL AUTO_INCREMENT,
    subject_id  BIGINT   NOT NULL,
    faculty_id  BIGINT   NOT NULL,
    semester_id BIGINT   NOT NULL,
    created_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_assignments_subject_sem (subject_id, semester_id),
    KEY idx_assignments_faculty (faculty_id),
    CONSTRAINT fk_assignments_subject FOREIGN KEY (subject_id) REFERENCES subjects (id),
    CONSTRAINT fk_assignments_faculty FOREIGN KEY (faculty_id) REFERENCES faculty (id),
    CONSTRAINT fk_assignments_semester FOREIGN KEY (semester_id) REFERENCES semesters (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE student_enrollments (
    id          BIGINT   NOT NULL AUTO_INCREMENT,
    student_id  BIGINT   NOT NULL,
    subject_id  BIGINT   NOT NULL,
    semester_id BIGINT   NOT NULL,
    created_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_enrollments (student_id, subject_id, semester_id),
    KEY idx_enrollments_subject_sem (subject_id, semester_id),
    KEY idx_enrollments_sem (semester_id),
    CONSTRAINT fk_enrollments_student FOREIGN KEY (student_id) REFERENCES students (id),
    CONSTRAINT fk_enrollments_subject FOREIGN KEY (subject_id) REFERENCES subjects (id),
    CONSTRAINT fk_enrollments_semester FOREIGN KEY (semester_id) REFERENCES semesters (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Marks life cycle: DRAFT -> SUBMITTED -> APPROVED | REJECTED(-> edit -> DRAFT) ; APPROVED -> PUBLISHED.
CREATE TABLE marks (
    id                 BIGINT       NOT NULL AUTO_INCREMENT,
    student_id         BIGINT       NOT NULL,
    subject_id         BIGINT       NOT NULL,
    examination_session_id BIGINT   NOT NULL,
    internal_marks     DECIMAL(6,2) NULL,
    external_marks     DECIMAL(6,2) NULL,
    status             ENUM('DRAFT','SUBMITTED','APPROVED','REJECTED','PUBLISHED') NOT NULL DEFAULT 'DRAFT',
    entered_by_faculty_id BIGINT    NOT NULL,
    submitted_at       DATETIME     NULL,
    reviewed_by_user_id BIGINT      NULL,
    reviewed_at        DATETIME     NULL,
    review_remarks     VARCHAR(500) NULL,
    created_at         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    -- prevents duplicate marks for the same student, subject and examination
    UNIQUE KEY uk_marks_student_subject_session (student_id, subject_id, examination_session_id),
    KEY idx_marks_session_subject_status (examination_session_id, subject_id, status),
    KEY idx_marks_status (status),
    KEY idx_marks_faculty (entered_by_faculty_id),
    CONSTRAINT fk_marks_student FOREIGN KEY (student_id) REFERENCES students (id),
    CONSTRAINT fk_marks_subject FOREIGN KEY (subject_id) REFERENCES subjects (id),
    CONSTRAINT fk_marks_session FOREIGN KEY (examination_session_id) REFERENCES examination_sessions (id),
    CONSTRAINT fk_marks_faculty FOREIGN KEY (entered_by_faculty_id) REFERENCES faculty (id),
    CONSTRAINT fk_marks_reviewer FOREIGN KEY (reviewed_by_user_id) REFERENCES users (id),
    CONSTRAINT chk_marks_nonneg CHECK ((internal_marks IS NULL OR internal_marks >= 0)
                                   AND (external_marks IS NULL OR external_marks >= 0))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Grading rules are configuration. Exactly one rule is "active"; results remember the rule used.
CREATE TABLE grading_rules (
    id                      BIGINT       NOT NULL AUTO_INCREMENT,
    name                    VARCHAR(100) NOT NULL,
    description             VARCHAR(500) NULL,
    sample_placeholder      TINYINT(1)   NOT NULL DEFAULT 0,  -- 1 = illustrative values, not an official regulation
    include_failed_in_gpa   TINYINT(1)   NOT NULL DEFAULT 1,  -- failed subjects' credits count in the GPA denominator
    active                  TINYINT(1)   NOT NULL DEFAULT 0,
    created_at              DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at              DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_grading_rules_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Grade boundaries: percentage of the subject's maximum total -> grade and grade point.
CREATE TABLE grading_rule_bands (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    grading_rule_id BIGINT       NOT NULL,
    grade           VARCHAR(5)   NOT NULL,
    min_percentage  DECIMAL(5,2) NOT NULL,
    grade_point     DECIMAL(4,2) NOT NULL,
    fail_grade      TINYINT(1)   NOT NULL DEFAULT 0,  -- 1 = this grade is awarded to failed subjects
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_bands_rule_grade (grading_rule_id, grade),
    UNIQUE KEY uk_bands_rule_min (grading_rule_id, min_percentage),
    CONSTRAINT fk_bands_rule FOREIGN KEY (grading_rule_id) REFERENCES grading_rules (id) ON DELETE CASCADE,
    CONSTRAINT chk_bands_pct CHECK (min_percentage BETWEEN 0 AND 100),
    CONSTRAINT chk_bands_gp CHECK (grade_point >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- One row per student per examination session; created only when an admin publishes.
CREATE TABLE results (
    id                     BIGINT       NOT NULL AUTO_INCREMENT,
    student_id             BIGINT       NOT NULL,
    examination_session_id BIGINT       NOT NULL,
    grading_rule_id        BIGINT       NOT NULL,
    total_credits          DECIMAL(6,2) NOT NULL,
    earned_credits         DECIMAL(6,2) NOT NULL,
    sgpa                   DECIMAL(4,2) NOT NULL,
    result_status          ENUM('PASS','FAIL') NOT NULL,
    revision               INT          NOT NULL DEFAULT 1,   -- incremented by an audited correction
    published_by_user_id   BIGINT       NOT NULL,
    published_at           DATETIME     NOT NULL,
    created_at             DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at             DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_results_student_session (student_id, examination_session_id),
    KEY idx_results_session (examination_session_id),
    CONSTRAINT fk_results_student FOREIGN KEY (student_id) REFERENCES students (id),
    CONSTRAINT fk_results_session FOREIGN KEY (examination_session_id) REFERENCES examination_sessions (id),
    CONSTRAINT fk_results_rule FOREIGN KEY (grading_rule_id) REFERENCES grading_rules (id),
    CONSTRAINT fk_results_publisher FOREIGN KEY (published_by_user_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Subject-wise snapshot of a published result (code/name/credits copied so later edits cannot alter history).
CREATE TABLE result_subjects (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    result_id       BIGINT       NOT NULL,
    subject_id      BIGINT       NOT NULL,
    mark_id         BIGINT       NOT NULL,
    subject_code    VARCHAR(20)  NOT NULL,
    subject_name    VARCHAR(150) NOT NULL,
    credits         DECIMAL(4,2) NOT NULL,
    internal_marks  DECIMAL(6,2) NOT NULL,
    external_marks  DECIMAL(6,2) NOT NULL,
    total_marks     DECIMAL(7,2) NOT NULL,
    max_marks       DECIMAL(7,2) NOT NULL,
    percentage      DECIMAL(5,2) NOT NULL,
    grade           VARCHAR(5)   NOT NULL,
    grade_point     DECIMAL(4,2) NOT NULL,
    passed          TINYINT(1)   NOT NULL,
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_result_subjects (result_id, subject_id),
    KEY idx_result_subjects_subject (subject_id),
    CONSTRAINT fk_result_subjects_result FOREIGN KEY (result_id) REFERENCES results (id) ON DELETE CASCADE,
    CONSTRAINT fk_result_subjects_subject FOREIGN KEY (subject_id) REFERENCES subjects (id),
    CONSTRAINT fk_result_subjects_mark FOREIGN KEY (mark_id) REFERENCES marks (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Authorized corrections of approved/published marks (the only way to change them).
CREATE TABLE mark_corrections (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    mark_id           BIGINT       NOT NULL,
    old_internal      DECIMAL(6,2) NULL,
    old_external      DECIMAL(6,2) NULL,
    new_internal      DECIMAL(6,2) NOT NULL,
    new_external      DECIMAL(6,2) NOT NULL,
    reason            VARCHAR(500) NOT NULL,
    corrected_by_user_id BIGINT    NOT NULL,
    created_at        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_corrections_mark (mark_id),
    CONSTRAINT fk_corrections_mark FOREIGN KEY (mark_id) REFERENCES marks (id),
    CONSTRAINT fk_corrections_user FOREIGN KEY (corrected_by_user_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE audit_logs (
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    actor_user_id  BIGINT       NULL,
    actor_username VARCHAR(50)  NULL,
    action         VARCHAR(50)  NOT NULL,
    entity_type    VARCHAR(50)  NOT NULL,
    entity_id      BIGINT       NULL,
    details        TEXT         NULL,                 -- JSON text with old/new values
    created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_audit_created (created_at),
    KEY idx_audit_entity (entity_type, entity_id),
    KEY idx_audit_action (action),
    CONSTRAINT fk_audit_user FOREIGN KEY (actor_user_id) REFERENCES users (id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
