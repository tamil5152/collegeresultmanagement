package com.college.result.repository;

import com.college.result.common.DbUtil;
import com.college.result.common.Paging;
import com.college.result.dto.ResultDtos.AdminResultRow;
import com.college.result.dto.ResultDtos.SubjectResult;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class ResultRepository {

    /** A scope row: one enrolled subject of one student plus the state of its marks (all null when missing). */
    public record ScopeRow(long studentId, String rollNumber, String studentName, long subjectId, String subjectCode,
                           Long markId, String markStatus, BigDecimal internal, BigDecimal external) {}

    public record ResultHeader(long id, long studentId, long sessionId, long ruleId, int revision) {}

    /** Joined header used to render a result for a student. */
    public record ResultRow(long id, long studentId, long sessionId, long ruleId, String ruleName,
                            boolean ruleSample, boolean includeFailed, BigDecimal totalCredits,
                            BigDecimal earnedCredits, BigDecimal sgpa, String status, int revision,
                            Instant publishedAt, String sessionName, String academicYear, int semesterNumber,
                            String semesterName, String rollNumber, String studentName, String departmentName,
                            int batchYear) {}

    public record AttemptRow(long resultId, long subjectId, BigDecimal credits, BigDecimal gradePoint,
                             boolean passed) {}

    private static final String ROW_SELECT = "SELECT r.id, r.student_id, r.examination_session_id, r.grading_rule_id, "
            + "gr.name AS rule_name, gr.sample_placeholder, gr.include_failed_in_gpa, r.total_credits, "
            + "r.earned_credits, r.sgpa, r.result_status, r.revision, r.published_at, es.name AS session_name, "
            + "ay.label AS year_label, sem.semester_number, sem.name AS sem_name, st.roll_number, "
            + "u.full_name, d.name AS dept_name, st.batch_year FROM results r "
            + "JOIN grading_rules gr ON gr.id = r.grading_rule_id "
            + "JOIN examination_sessions es ON es.id = r.examination_session_id "
            + "JOIN semesters sem ON sem.id = es.semester_id JOIN academic_years ay ON ay.id = sem.academic_year_id "
            + "JOIN students st ON st.id = r.student_id JOIN users u ON u.id = st.user_id "
            + "JOIN departments d ON d.id = st.department_id ";
    private static final RowMapper<ResultRow> ROW = (rs, i) -> new ResultRow(rs.getLong("id"), rs.getLong("student_id"),
            rs.getLong("examination_session_id"), rs.getLong("grading_rule_id"), rs.getString("rule_name"),
            rs.getBoolean("sample_placeholder"), rs.getBoolean("include_failed_in_gpa"),
            rs.getBigDecimal("total_credits"), rs.getBigDecimal("earned_credits"), rs.getBigDecimal("sgpa"),
            rs.getString("result_status"), rs.getInt("revision"), DbUtil.instant(rs, "published_at"),
            rs.getString("session_name"), rs.getString("year_label"), rs.getInt("semester_number"),
            rs.getString("sem_name"), rs.getString("roll_number"), rs.getString("full_name"),
            rs.getString("dept_name"), rs.getInt("batch_year"));

    private final JdbcTemplate jdbc;

    public ResultRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // ------------------------------------------------------------------ publication
    public List<ScopeRow> scope(long sessionId, long semesterId, long departmentId) {
        return jdbc.query("SELECT e.student_id, st.roll_number, u.full_name, e.subject_id, sub.code, m.id AS mark_id, "
                        + "m.status, m.internal_marks, m.external_marks FROM student_enrollments e "
                        + "JOIN students st ON st.id = e.student_id JOIN users u ON u.id = st.user_id "
                        + "JOIN subjects sub ON sub.id = e.subject_id "
                        + "LEFT JOIN marks m ON m.student_id = e.student_id AND m.subject_id = e.subject_id "
                        + "AND m.examination_session_id = ? "
                        + "WHERE e.semester_id = ? AND st.department_id = ? AND u.active = 1 "
                        + "AND NOT EXISTS (SELECT 1 FROM results r WHERE r.student_id = e.student_id "
                        + "AND r.examination_session_id = ?) ORDER BY st.roll_number, sub.code",
                (rs, i) -> new ScopeRow(rs.getLong("student_id"), rs.getString("roll_number"),
                        rs.getString("full_name"), rs.getLong("subject_id"), rs.getString("code"),
                        DbUtil.nullableLong(rs, "mark_id"), rs.getString("status"),
                        rs.getBigDecimal("internal_marks"), rs.getBigDecimal("external_marks")),
                sessionId, semesterId, departmentId, sessionId);
    }

    public long countAlreadyPublished(long sessionId, long departmentId) {
        Long n = jdbc.queryForObject("SELECT COUNT(*) FROM results r JOIN students st ON st.id = r.student_id "
                + "WHERE r.examination_session_id = ? AND st.department_id = ?", Long.class, sessionId, departmentId);
        return n == null ? 0 : n;
    }

    public long insertResult(long studentId, long sessionId, long ruleId, BigDecimal totalCredits,
                             BigDecimal earnedCredits, BigDecimal sgpa, String status, long publisherUserId) {
        return DbUtil.insert(jdbc, "INSERT INTO results (student_id, examination_session_id, grading_rule_id, "
                + "total_credits, earned_credits, sgpa, result_status, published_by_user_id, published_at) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, NOW())", studentId, sessionId, ruleId, totalCredits,
                earnedCredits, sgpa, status, publisherUserId);
    }

    public void insertSubject(long resultId, long subjectId, long markId, String code, String name, BigDecimal credits,
                              BigDecimal internal, BigDecimal external, BigDecimal total, BigDecimal max,
                              BigDecimal percentage, String grade, BigDecimal gradePoint, boolean passed) {
        jdbc.update("INSERT INTO result_subjects (result_id, subject_id, mark_id, subject_code, subject_name, credits, "
                + "internal_marks, external_marks, total_marks, max_marks, percentage, grade, grade_point, passed) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)", resultId, subjectId, markId, code, name,
                credits, internal, external, total, max, percentage, grade, gradePoint, passed);
    }

    public Optional<ResultHeader> lockByStudentAndSession(long studentId, long sessionId) {
        return jdbc.query("SELECT id, student_id, examination_session_id, grading_rule_id, revision FROM results "
                        + "WHERE student_id = ? AND examination_session_id = ? FOR UPDATE",
                (rs, i) -> new ResultHeader(rs.getLong("id"), rs.getLong("student_id"),
                        rs.getLong("examination_session_id"), rs.getLong("grading_rule_id"), rs.getInt("revision")),
                studentId, sessionId).stream().findFirst();
    }

    public void deleteSubjects(long resultId) {
        jdbc.update("DELETE FROM result_subjects WHERE result_id = ?", resultId);
    }

    public void updateTotals(long resultId, BigDecimal totalCredits, BigDecimal earnedCredits, BigDecimal sgpa,
                             String status) {
        jdbc.update("UPDATE results SET total_credits = ?, earned_credits = ?, sgpa = ?, result_status = ?, "
                + "revision = revision + 1 WHERE id = ?", totalCredits, earnedCredits, sgpa, status, resultId);
    }

    // ------------------------------------------------------------------ student reads
    public List<ResultRow> rowsForStudent(long studentId) {
        return jdbc.query(ROW_SELECT + "WHERE r.student_id = ? ORDER BY es.start_date, es.id, r.id", ROW, studentId);
    }

    public Optional<ResultRow> findRow(long resultId) {
        return jdbc.query(ROW_SELECT + "WHERE r.id = ?", ROW, resultId).stream().findFirst();
    }

    public List<AttemptRow> attemptsForStudent(long studentId) {
        return jdbc.query("SELECT r.id AS result_id, rs.subject_id, rs.credits, rs.grade_point, rs.passed "
                        + "FROM results r JOIN examination_sessions es ON es.id = r.examination_session_id "
                        + "JOIN result_subjects rs ON rs.result_id = r.id WHERE r.student_id = ? "
                        + "ORDER BY es.start_date, es.id, r.id, rs.id",
                (rs, i) -> new AttemptRow(rs.getLong("result_id"), rs.getLong("subject_id"),
                        rs.getBigDecimal("credits"), rs.getBigDecimal("grade_point"), rs.getBoolean("passed")),
                studentId);
    }

    public List<SubjectResult> subjects(long resultId) {
        return jdbc.query("SELECT subject_code, subject_name, credits, internal_marks, external_marks, total_marks, "
                        + "max_marks, percentage, grade, grade_point, passed FROM result_subjects "
                        + "WHERE result_id = ? ORDER BY subject_code",
                (rs, i) -> new SubjectResult(rs.getString("subject_code"), rs.getString("subject_name"),
                        rs.getBigDecimal("credits"), rs.getBigDecimal("internal_marks"),
                        rs.getBigDecimal("external_marks"), rs.getBigDecimal("total_marks"),
                        rs.getBigDecimal("max_marks"), rs.getBigDecimal("percentage"), rs.getString("grade"),
                        rs.getBigDecimal("grade_point"), rs.getBoolean("passed")), resultId);
    }

    // ------------------------------------------------------------------ admin reads
    private static final String ADMIN_FILTER = "WHERE (st.roll_number LIKE ? OR u.full_name LIKE ?) "
            + "AND (? = 0 OR r.examination_session_id = ?) AND (? = 0 OR st.department_id = ?) ";

    public List<AdminResultRow> searchAdmin(String search, long sessionId, long departmentId, Paging p) {
        String like = Paging.like(search);
        return jdbc.query("SELECT r.id, r.student_id, st.roll_number, u.full_name, d.code, r.examination_session_id, "
                        + "es.name AS session_name, r.sgpa, r.result_status, r.revision, r.published_at FROM results r "
                        + "JOIN students st ON st.id = r.student_id JOIN users u ON u.id = st.user_id "
                        + "JOIN departments d ON d.id = st.department_id "
                        + "JOIN examination_sessions es ON es.id = r.examination_session_id " + ADMIN_FILTER
                        + "ORDER BY r.published_at DESC, st.roll_number LIMIT ? OFFSET ?",
                (rs, i) -> new AdminResultRow(rs.getLong("id"), rs.getLong("student_id"), rs.getString("roll_number"),
                        rs.getString("full_name"), rs.getString("code"), rs.getLong("examination_session_id"),
                        rs.getString("session_name"), rs.getBigDecimal("sgpa"), rs.getString("result_status"),
                        rs.getInt("revision"), DbUtil.instant(rs, "published_at")),
                like, like, sessionId, sessionId, departmentId, departmentId, p.size(), p.offset());
    }

    public long countAdmin(String search, long sessionId, long departmentId) {
        String like = Paging.like(search);
        Long n = jdbc.queryForObject("SELECT COUNT(*) FROM results r JOIN students st ON st.id = r.student_id "
                + "JOIN users u ON u.id = st.user_id " + ADMIN_FILTER, Long.class, like, like, sessionId, sessionId,
                departmentId, departmentId);
        return n == null ? 0 : n;
    }

    public long countAll() {
        Long n = jdbc.queryForObject("SELECT COUNT(*) FROM results", Long.class);
        return n == null ? 0 : n;
    }
}
