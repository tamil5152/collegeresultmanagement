package com.college.result.repository;

import com.college.result.common.DbUtil;
import com.college.result.common.Paging;
import com.college.result.dto.AssignmentDtos.EnrollmentResponse;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class EnrollmentRepository {

    public record EnrolledStudent(long studentId, String rollNumber, String fullName) {}

    private static final String SELECT = "SELECT e.id, e.student_id, st.roll_number, u.full_name, e.subject_id, "
            + "sub.code AS sub_code, sub.name AS sub_name, e.semester_id, CONCAT(ay.label, ' - ', sem.name) AS sem_label "
            + "FROM student_enrollments e JOIN students st ON st.id = e.student_id JOIN users u ON u.id = st.user_id "
            + "JOIN subjects sub ON sub.id = e.subject_id JOIN semesters sem ON sem.id = e.semester_id "
            + "JOIN academic_years ay ON ay.id = sem.academic_year_id ";
    private static final String FILTER = "WHERE (st.roll_number LIKE ? OR u.full_name LIKE ?) "
            + "AND (? = 0 OR e.semester_id = ?) AND (? = 0 OR st.department_id = ?) AND (? = 0 OR e.subject_id = ?) ";
    private static final RowMapper<EnrollmentResponse> MAPPER = (rs, i) -> new EnrollmentResponse(rs.getLong("id"),
            rs.getLong("student_id"), rs.getString("roll_number"), rs.getString("full_name"), rs.getLong("subject_id"),
            rs.getString("sub_code"), rs.getString("sub_name"), rs.getLong("semester_id"), rs.getString("sem_label"));

    private final JdbcTemplate jdbc;

    public EnrollmentRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<EnrollmentResponse> search(String search, long semesterId, long departmentId, long subjectId,
                                           Paging p) {
        String like = Paging.like(search);
        return jdbc.query(SELECT + FILTER + "ORDER BY st.roll_number, sub.code LIMIT ? OFFSET ?", MAPPER, like, like,
                semesterId, semesterId, departmentId, departmentId, subjectId, subjectId, p.size(), p.offset());
    }

    public long count(String search, long semesterId, long departmentId, long subjectId) {
        String like = Paging.like(search);
        Long n = jdbc.queryForObject("SELECT COUNT(*) FROM student_enrollments e JOIN students st "
                + "ON st.id = e.student_id JOIN users u ON u.id = st.user_id " + FILTER, Long.class, like, like,
                semesterId, semesterId, departmentId, departmentId, subjectId, subjectId);
        return n == null ? 0 : n;
    }

    public Optional<EnrollmentResponse> findResponse(long id) {
        return jdbc.query(SELECT + "WHERE e.id = ?", MAPPER, id).stream().findFirst();
    }

    public long insert(long studentId, long subjectId, long semesterId) {
        return DbUtil.insert(jdbc, "INSERT INTO student_enrollments (student_id, subject_id, semester_id) "
                + "VALUES (?, ?, ?)", studentId, subjectId, semesterId);
    }

    /** Enrols every active student of the department currently in {@code semesterNumber} in all active subjects. */
    public int bulkEnroll(long semesterId, long departmentId, int semesterNumber) {
        return jdbc.update("INSERT INTO student_enrollments (student_id, subject_id, semester_id) "
                + "SELECT st.id, sub.id, ? FROM students st JOIN users u ON u.id = st.user_id "
                + "JOIN subjects sub ON sub.department_id = st.department_id "
                + "AND sub.semester_number = st.current_semester "
                + "WHERE st.department_id = ? AND st.current_semester = ? AND u.active = 1 AND sub.active = 1 "
                + "AND NOT EXISTS (SELECT 1 FROM student_enrollments x WHERE x.student_id = st.id "
                + "AND x.subject_id = sub.id AND x.semester_id = ?)", semesterId, departmentId, semesterNumber,
                semesterId);
    }

    public int delete(long id) {
        return jdbc.update("DELETE FROM student_enrollments WHERE id = ?", id);
    }

    public boolean hasMarks(long enrollmentId) {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM student_enrollments e JOIN examination_sessions es "
                + "ON es.semester_id = e.semester_id JOIN marks m ON m.examination_session_id = es.id "
                + "AND m.student_id = e.student_id AND m.subject_id = e.subject_id WHERE e.id = ?", Integer.class,
                enrollmentId);
        return n != null && n > 0;
    }

    public List<EnrolledStudent> enrolledStudents(long subjectId, long semesterId) {
        return jdbc.query("SELECT st.id, st.roll_number, u.full_name FROM student_enrollments e "
                + "JOIN students st ON st.id = e.student_id JOIN users u ON u.id = st.user_id "
                + "WHERE e.subject_id = ? AND e.semester_id = ? ORDER BY st.roll_number",
                (rs, i) -> new EnrolledStudent(rs.getLong("id"), rs.getString("roll_number"),
                        rs.getString("full_name")), subjectId, semesterId);
    }
}
