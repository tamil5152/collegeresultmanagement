package com.college.result.repository;

import com.college.result.common.DbUtil;
import com.college.result.common.Paging;
import com.college.result.dto.UserDtos.StudentResponse;
import com.college.result.dto.UserDtos.StudentUpdateRequest;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class StudentRepository {

    private static final String SELECT = "SELECT st.id, st.user_id, u.username, u.email, u.full_name, "
            + "st.department_id, d.code AS dept_code, d.name AS dept_name, st.roll_number, st.batch_year, "
            + "st.current_semester, st.phone, st.date_of_birth, u.active FROM students st "
            + "JOIN users u ON u.id = st.user_id JOIN departments d ON d.id = st.department_id ";
    private static final String FILTER = "WHERE (st.roll_number LIKE ? OR u.full_name LIKE ? OR u.username LIKE ?) "
            + "AND (? = 0 OR st.department_id = ?) AND (? = 0 OR st.current_semester = ?) "
            + "AND (? = -1 OR u.active = ?) ";
    private static final RowMapper<StudentResponse> MAPPER = (rs, i) -> new StudentResponse(rs.getLong("id"),
            rs.getLong("user_id"), rs.getString("username"), rs.getString("email"), rs.getString("full_name"),
            rs.getLong("department_id"), rs.getString("dept_code"), rs.getString("dept_name"),
            rs.getString("roll_number"), rs.getInt("batch_year"), rs.getInt("current_semester"),
            rs.getString("phone"), DbUtil.date(rs, "date_of_birth"), rs.getBoolean("active"));

    private final JdbcTemplate jdbc;

    public StudentRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** active: -1 = any, 1 = active only, 0 = inactive only. */
    public List<StudentResponse> search(String search, long departmentId, int semester, int active, Paging p) {
        String like = Paging.like(search);
        return jdbc.query(SELECT + FILTER + "ORDER BY st.roll_number LIMIT ? OFFSET ?", MAPPER, like, like, like,
                departmentId, departmentId, semester, semester, active, active, p.size(), p.offset());
    }

    public long count(String search, long departmentId, int semester, int active) {
        String like = Paging.like(search);
        Long n = jdbc.queryForObject("SELECT COUNT(*) FROM students st JOIN users u ON u.id = st.user_id " + FILTER,
                Long.class, like, like, like, departmentId, departmentId, semester, semester, active, active);
        return n == null ? 0 : n;
    }

    public Optional<StudentResponse> findById(long id) {
        return jdbc.query(SELECT + "WHERE st.id = ?", MAPPER, id).stream().findFirst();
    }

    public Optional<StudentResponse> findByUserId(long userId) {
        return jdbc.query(SELECT + "WHERE st.user_id = ?", MAPPER, userId).stream().findFirst();
    }

    public long insert(long userId, long departmentId, String roll, int batchYear, int semester, String phone,
                       LocalDate dob) {
        return DbUtil.insert(jdbc, "INSERT INTO students (user_id, department_id, roll_number, batch_year, "
                + "current_semester, phone, date_of_birth) VALUES (?, ?, ?, ?, ?, ?, ?)", userId, departmentId, roll,
                batchYear, semester, phone, dob);
    }

    public int update(long id, StudentUpdateRequest r, String roll) {
        return jdbc.update("UPDATE students SET department_id = ?, roll_number = ?, batch_year = ?, "
                + "current_semester = ?, phone = ?, date_of_birth = ? WHERE id = ?", r.departmentId(), roll,
                r.batchYear(), r.currentSemester(), r.phone(), r.dateOfBirth(), id);
    }

    public boolean rollTaken(String roll, long excludeId) {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM students WHERE roll_number = ? AND id <> ?",
                Integer.class, roll, excludeId);
        return n != null && n > 0;
    }
}
