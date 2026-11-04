package com.college.result.repository;

import com.college.result.common.DbUtil;
import com.college.result.common.Paging;
import com.college.result.dto.MarksDtos.MarkReviewRow;
import com.college.result.dto.MarksDtos.MarkSheetRow;
import com.college.result.dto.MarksDtos.SubmissionSummary;
import com.college.result.model.Mark;
import com.college.result.model.MarkStatus;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class MarksRepository {

    private static final RowMapper<Mark> MODEL = (rs, i) -> new Mark(rs.getLong("id"), rs.getLong("student_id"),
            rs.getLong("subject_id"), rs.getLong("examination_session_id"), rs.getBigDecimal("internal_marks"),
            rs.getBigDecimal("external_marks"), MarkStatus.valueOf(rs.getString("status")),
            rs.getLong("entered_by_faculty_id"));
    private static final String MODEL_COLS = "id, student_id, subject_id, examination_session_id, internal_marks, "
            + "external_marks, status, entered_by_faculty_id";

    private static final String REVIEW_SELECT = "SELECT m.id, m.student_id, st.roll_number, u.full_name AS student_name, "
            + "d.code AS dept_code, m.subject_id, sub.code AS sub_code, sub.name AS sub_name, "
            + "m.examination_session_id, es.name AS session_name, m.internal_marks, m.external_marks, "
            + "sub.internal_max, sub.external_max, m.status, fu.full_name AS faculty_name, m.submitted_at, "
            + "m.reviewed_at, m.review_remarks FROM marks m JOIN students st ON st.id = m.student_id "
            + "JOIN users u ON u.id = st.user_id JOIN departments d ON d.id = st.department_id "
            + "JOIN subjects sub ON sub.id = m.subject_id JOIN examination_sessions es ON es.id = m.examination_session_id "
            + "JOIN faculty f ON f.id = m.entered_by_faculty_id JOIN users fu ON fu.id = f.user_id ";
    private static final String REVIEW_FILTER = "WHERE (st.roll_number LIKE ? OR u.full_name LIKE ? OR sub.code LIKE ?) "
            + "AND (? = 0 OR m.examination_session_id = ?) AND (? = 0 OR st.department_id = ?) "
            + "AND (? = 0 OR m.subject_id = ?) AND (? = '' OR m.status = ?) ";
    private static final RowMapper<MarkReviewRow> REVIEW = (rs, i) -> new MarkReviewRow(rs.getLong("id"),
            rs.getLong("student_id"), rs.getString("roll_number"), rs.getString("student_name"),
            rs.getString("dept_code"), rs.getLong("subject_id"), rs.getString("sub_code"), rs.getString("sub_name"),
            rs.getLong("examination_session_id"), rs.getString("session_name"), rs.getBigDecimal("internal_marks"),
            rs.getBigDecimal("external_marks"), rs.getBigDecimal("internal_max"), rs.getBigDecimal("external_max"),
            rs.getString("status"), rs.getString("faculty_name"), DbUtil.instant(rs, "submitted_at"),
            DbUtil.instant(rs, "reviewed_at"), rs.getString("review_remarks"));

    private final JdbcTemplate jdbc;

    public MarksRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // ------------------------------------------------------------------ single / locked reads
    public Optional<Mark> findByIdForUpdate(long id) {
        return jdbc.query("SELECT " + MODEL_COLS + " FROM marks WHERE id = ? FOR UPDATE", MODEL, id).stream().findFirst();
    }

    public List<Mark> findByIdsForUpdate(List<Long> ids) {
        return jdbc.query("SELECT " + MODEL_COLS + " FROM marks WHERE id IN (" + DbUtil.placeholders(ids.size())
                + ") ORDER BY id FOR UPDATE", MODEL, ids.toArray());
    }

    public List<Mark> findBySessionAndSubjectForUpdate(long sessionId, long subjectId) {
        return jdbc.query("SELECT " + MODEL_COLS + " FROM marks WHERE examination_session_id = ? AND subject_id = ? "
                + "ORDER BY id FOR UPDATE", MODEL, sessionId, subjectId);
    }

    public List<Mark> findForStudentSession(long studentId, long sessionId) {
        return jdbc.query("SELECT " + MODEL_COLS + " FROM marks WHERE student_id = ? AND examination_session_id = ?",
                MODEL, studentId, sessionId);
    }

    // ------------------------------------------------------------------ writes
    public long insert(long studentId, long subjectId, long sessionId, BigDecimal internal, BigDecimal external,
                       long facultyId) {
        return DbUtil.insert(jdbc, "INSERT INTO marks (student_id, subject_id, examination_session_id, "
                + "internal_marks, external_marks, status, entered_by_faculty_id) VALUES (?, ?, ?, ?, ?, 'DRAFT', ?)",
                studentId, subjectId, sessionId, internal, external, facultyId);
    }

    /** Faculty edit: back to DRAFT so it must be submitted again. */
    public void updateDraft(long id, BigDecimal internal, BigDecimal external, long facultyId) {
        jdbc.update("UPDATE marks SET internal_marks = ?, external_marks = ?, status = 'DRAFT', "
                + "entered_by_faculty_id = ? WHERE id = ?", internal, external, facultyId, id);
    }

    public void markSubmitted(long id) {
        jdbc.update("UPDATE marks SET status = 'SUBMITTED', submitted_at = NOW(), review_remarks = NULL WHERE id = ?", id);
    }

    public void review(long id, MarkStatus status, long reviewerUserId, String remarks) {
        jdbc.update("UPDATE marks SET status = ?, reviewed_by_user_id = ?, reviewed_at = NOW(), review_remarks = ? "
                + "WHERE id = ?", status.name(), reviewerUserId, remarks, id);
    }

    public void applyCorrection(long id, BigDecimal internal, BigDecimal external) {
        jdbc.update("UPDATE marks SET internal_marks = ?, external_marks = ? WHERE id = ?", internal, external, id);
    }

    public void insertCorrection(long markId, BigDecimal oldInternal, BigDecimal oldExternal, BigDecimal newInternal,
                                 BigDecimal newExternal, String reason, long userId) {
        jdbc.update("INSERT INTO mark_corrections (mark_id, old_internal, old_external, new_internal, new_external, "
                + "reason, corrected_by_user_id) VALUES (?, ?, ?, ?, ?, ?, ?)", markId, oldInternal, oldExternal,
                newInternal, newExternal, reason, userId);
    }

    public void setPublished(List<Long> ids) {
        if (ids.isEmpty()) {
            return;
        }
        jdbc.update("UPDATE marks SET status = 'PUBLISHED' WHERE id IN (" + DbUtil.placeholders(ids.size()) + ")",
                ids.toArray());
    }

    // ------------------------------------------------------------------ faculty sheet
    public List<MarkSheetRow> sheetRows(long subjectId, long semesterId, long sessionId, boolean sessionOpen) {
        return jdbc.query("SELECT st.id AS student_id, st.roll_number, u.full_name, m.id AS mark_id, m.internal_marks, "
                        + "m.external_marks, m.status, m.review_remarks FROM student_enrollments e "
                        + "JOIN students st ON st.id = e.student_id JOIN users u ON u.id = st.user_id "
                        + "LEFT JOIN marks m ON m.student_id = e.student_id AND m.subject_id = e.subject_id "
                        + "AND m.examination_session_id = ? WHERE e.subject_id = ? AND e.semester_id = ? "
                        + "ORDER BY st.roll_number", (rs, i) -> {
                    String status = rs.getString("status");
                    boolean editable = sessionOpen && (status == null || MarkStatus.valueOf(status).editableByFaculty());
                    return new MarkSheetRow(rs.getLong("student_id"), rs.getString("roll_number"),
                            rs.getString("full_name"), DbUtil.nullableLong(rs, "mark_id"),
                            rs.getBigDecimal("internal_marks"), rs.getBigDecimal("external_marks"),
                            status == null ? "NOT_ENTERED" : status, rs.getString("review_remarks"), editable);
                }, sessionId, subjectId, semesterId);
    }

    public List<SubmissionSummary> submissionSummaries(long facultyId) {
        return jdbc.query("SELECT a.id AS assignment_id, es.id AS session_id, es.name AS session_name, "
                + "es.status AS session_status, sub.code, sub.name, "
                + "(SELECT COUNT(*) FROM student_enrollments e WHERE e.subject_id = a.subject_id "
                + "AND e.semester_id = a.semester_id) AS enrolled, "
                + "COUNT(m.id) AS entered, COALESCE(SUM(m.status = 'DRAFT'), 0) AS draft, "
                + "COALESCE(SUM(m.status = 'SUBMITTED'), 0) AS submitted, COALESCE(SUM(m.status = 'APPROVED'), 0) AS approved, "
                + "COALESCE(SUM(m.status = 'REJECTED'), 0) AS rejected, COALESCE(SUM(m.status = 'PUBLISHED'), 0) AS published "
                + "FROM subject_assignments a JOIN subjects sub ON sub.id = a.subject_id "
                + "JOIN examination_sessions es ON es.semester_id = a.semester_id "
                + "LEFT JOIN marks m ON m.subject_id = a.subject_id AND m.examination_session_id = es.id "
                + "WHERE a.faculty_id = ? GROUP BY a.id, es.id, es.name, es.status, sub.code, sub.name, a.subject_id, "
                + "a.semester_id, es.start_date ORDER BY es.start_date DESC, sub.code", (rs, i) -> {
                    long enrolled = rs.getLong("enrolled");
                    return new SubmissionSummary(rs.getLong("assignment_id"), rs.getLong("session_id"),
                            rs.getString("session_name"), rs.getString("session_status"), rs.getString("code"),
                            rs.getString("name"), enrolled, Math.max(0, enrolled - rs.getLong("entered")),
                            rs.getLong("draft"), rs.getLong("submitted"), rs.getLong("approved"),
                            rs.getLong("rejected"), rs.getLong("published"));
                }, facultyId);
    }

    public long countByFacultyAndStatus(long facultyId, String... statuses) {
        Long n = jdbc.queryForObject("SELECT COUNT(*) FROM marks m JOIN subject_assignments a "
                + "ON a.subject_id = m.subject_id AND a.faculty_id = ? JOIN examination_sessions es "
                + "ON es.id = m.examination_session_id AND es.semester_id = a.semester_id WHERE m.status IN ("
                + DbUtil.placeholders(statuses.length) + ")", Long.class, concat(facultyId, statuses));
        return n == null ? 0 : n;
    }

    public long countOpenSessionsForFaculty(long facultyId) {
        Long n = jdbc.queryForObject("SELECT COUNT(DISTINCT es.id) FROM subject_assignments a "
                + "JOIN examination_sessions es ON es.semester_id = a.semester_id AND es.status = 'OPEN' "
                + "WHERE a.faculty_id = ?", Long.class, facultyId);
        return n == null ? 0 : n;
    }

    private static Object[] concat(long first, String[] rest) {
        Object[] all = new Object[rest.length + 1];
        all[0] = first;
        System.arraycopy(rest, 0, all, 1, rest.length);
        return all;
    }

    // ------------------------------------------------------------------ admin review list
    public List<MarkReviewRow> searchReview(String search, long sessionId, long departmentId, long subjectId,
                                            String status, Paging p) {
        String like = Paging.like(search);
        return jdbc.query(REVIEW_SELECT + REVIEW_FILTER + "ORDER BY FIELD(m.status, 'SUBMITTED') DESC, es.start_date DESC, "
                + "sub.code, st.roll_number LIMIT ? OFFSET ?", REVIEW, like, like, like, sessionId, sessionId,
                departmentId, departmentId, subjectId, subjectId, status, status, p.size(), p.offset());
    }

    public long countReview(String search, long sessionId, long departmentId, long subjectId, String status) {
        String like = Paging.like(search);
        Long n = jdbc.queryForObject("SELECT COUNT(*) FROM marks m JOIN students st ON st.id = m.student_id "
                + "JOIN users u ON u.id = st.user_id JOIN subjects sub ON sub.id = m.subject_id " + REVIEW_FILTER,
                Long.class, like, like, like, sessionId, sessionId, departmentId, departmentId, subjectId, subjectId,
                status, status);
        return n == null ? 0 : n;
    }

    public long countByStatus(String status) {
        Long n = jdbc.queryForObject("SELECT COUNT(*) FROM marks WHERE status = ?", Long.class, status);
        return n == null ? 0 : n;
    }
}
