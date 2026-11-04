package com.college.result.repository;

import com.college.result.common.DbUtil;
import com.college.result.common.Paging;
import com.college.result.dto.AssignmentDtos.AssignmentResponse;
import com.college.result.model.Assignment;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class AssignmentRepository {

    private static final String SELECT = "SELECT a.id, a.subject_id, sub.code AS sub_code, sub.name AS sub_name, "
            + "sub.semester_number, a.faculty_id, u.full_name AS faculty_name, f.employee_code, a.semester_id, "
            + "CONCAT(ay.label, ' - ', sem.name) AS sem_label, "
            + "(SELECT COUNT(*) FROM student_enrollments e WHERE e.subject_id = a.subject_id "
            + "AND e.semester_id = a.semester_id) AS enrolled "
            + "FROM subject_assignments a JOIN subjects sub ON sub.id = a.subject_id "
            + "JOIN faculty f ON f.id = a.faculty_id JOIN users u ON u.id = f.user_id "
            + "JOIN semesters sem ON sem.id = a.semester_id JOIN academic_years ay ON ay.id = sem.academic_year_id ";
    private static final String FILTER = "WHERE (sub.code LIKE ? OR sub.name LIKE ? OR u.full_name LIKE ?) "
            + "AND (? = 0 OR a.semester_id = ?) AND (? = 0 OR a.faculty_id = ?) AND (? = 0 OR sub.department_id = ?) ";
    private static final RowMapper<AssignmentResponse> MAPPER = (rs, i) -> new AssignmentResponse(rs.getLong("id"),
            rs.getLong("subject_id"), rs.getString("sub_code"), rs.getString("sub_name"), rs.getInt("semester_number"),
            rs.getLong("faculty_id"), rs.getString("faculty_name"), rs.getString("employee_code"),
            rs.getLong("semester_id"), rs.getString("sem_label"), rs.getLong("enrolled"));
    private static final RowMapper<Assignment> MODEL = (rs, i) -> new Assignment(rs.getLong("id"),
            rs.getLong("subject_id"), rs.getLong("faculty_id"), rs.getLong("semester_id"));

    private final JdbcTemplate jdbc;

    public AssignmentRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<AssignmentResponse> search(String search, long semesterId, long facultyId, long departmentId,
                                           Paging p) {
        String like = Paging.like(search);
        return jdbc.query(SELECT + FILTER + "ORDER BY ay.start_date DESC, sub.code LIMIT ? OFFSET ?", MAPPER, like,
                like, like, semesterId, semesterId, facultyId, facultyId, departmentId, departmentId, p.size(),
                p.offset());
    }

    public long count(String search, long semesterId, long facultyId, long departmentId) {
        String like = Paging.like(search);
        Long n = jdbc.queryForObject("SELECT COUNT(*) FROM subject_assignments a JOIN subjects sub "
                + "ON sub.id = a.subject_id JOIN faculty f ON f.id = a.faculty_id JOIN users u ON u.id = f.user_id "
                + FILTER, Long.class, like, like, like, semesterId, semesterId, facultyId, facultyId, departmentId,
                departmentId);
        return n == null ? 0 : n;
    }

    public List<AssignmentResponse> findByFaculty(long facultyId) {
        return jdbc.query(SELECT + "WHERE a.faculty_id = ? ORDER BY ay.start_date DESC, sub.code", MAPPER, facultyId);
    }

    public Optional<AssignmentResponse> findResponse(long id) {
        return jdbc.query(SELECT + "WHERE a.id = ?", MAPPER, id).stream().findFirst();
    }

    public Optional<Assignment> findById(long id) {
        return jdbc.query("SELECT id, subject_id, faculty_id, semester_id FROM subject_assignments WHERE id = ?",
                MODEL, id).stream().findFirst();
    }

    public long insert(long subjectId, long facultyId, long semesterId) {
        return DbUtil.insert(jdbc, "INSERT INTO subject_assignments (subject_id, faculty_id, semester_id) "
                + "VALUES (?, ?, ?)", subjectId, facultyId, semesterId);
    }

    public int updateFaculty(long id, long facultyId) {
        return jdbc.update("UPDATE subject_assignments SET faculty_id = ? WHERE id = ?", facultyId, id);
    }

    public int delete(long id) {
        return jdbc.update("DELETE FROM subject_assignments WHERE id = ?", id);
    }

    public boolean hasMarks(long subjectId, long semesterId) {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM marks m JOIN examination_sessions es "
                + "ON es.id = m.examination_session_id WHERE m.subject_id = ? AND es.semester_id = ?", Integer.class,
                subjectId, semesterId);
        return n != null && n > 0;
    }
}
