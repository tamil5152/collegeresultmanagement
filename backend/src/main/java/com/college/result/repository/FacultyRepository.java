package com.college.result.repository;

import com.college.result.common.Paging;
import com.college.result.common.DbUtil;
import com.college.result.dto.UserDtos.FacultyResponse;
import com.college.result.dto.UserDtos.FacultyUpdateRequest;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class FacultyRepository {

    private static final String SELECT = "SELECT f.id, f.user_id, u.username, u.email, u.full_name, f.department_id, "
            + "d.code AS dept_code, f.employee_code, f.designation, f.phone, u.active FROM faculty f "
            + "JOIN users u ON u.id = f.user_id JOIN departments d ON d.id = f.department_id ";
    private static final String FILTER = "WHERE (f.employee_code LIKE ? OR u.full_name LIKE ? OR u.username LIKE ?) "
            + "AND (? = 0 OR f.department_id = ?) AND (? = -1 OR u.active = ?) ";
    private static final RowMapper<FacultyResponse> MAPPER = (rs, i) -> new FacultyResponse(rs.getLong("id"),
            rs.getLong("user_id"), rs.getString("username"), rs.getString("email"), rs.getString("full_name"),
            rs.getLong("department_id"), rs.getString("dept_code"), rs.getString("employee_code"),
            rs.getString("designation"), rs.getString("phone"), rs.getBoolean("active"));

    private final JdbcTemplate jdbc;

    public FacultyRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<FacultyResponse> search(String search, long departmentId, int active, Paging p) {
        String like = Paging.like(search);
        return jdbc.query(SELECT + FILTER + "ORDER BY u.full_name LIMIT ? OFFSET ?", MAPPER, like, like, like,
                departmentId, departmentId, active, active, p.size(), p.offset());
    }

    public long count(String search, long departmentId, int active) {
        String like = Paging.like(search);
        Long n = jdbc.queryForObject("SELECT COUNT(*) FROM faculty f JOIN users u ON u.id = f.user_id " + FILTER,
                Long.class, like, like, like, departmentId, departmentId, active, active);
        return n == null ? 0 : n;
    }

    public Optional<FacultyResponse> findById(long id) {
        return jdbc.query(SELECT + "WHERE f.id = ?", MAPPER, id).stream().findFirst();
    }

    public Optional<FacultyResponse> findByUserId(long userId) {
        return jdbc.query(SELECT + "WHERE f.user_id = ?", MAPPER, userId).stream().findFirst();
    }

    public long insert(long userId, long departmentId, String code, String designation, String phone) {
        return DbUtil.insert(jdbc, "INSERT INTO faculty (user_id, department_id, employee_code, designation, phone) "
                + "VALUES (?, ?, ?, ?, ?)", userId, departmentId, code, designation, phone);
    }

    public int update(long id, FacultyUpdateRequest r, String code) {
        return jdbc.update("UPDATE faculty SET department_id = ?, employee_code = ?, designation = ?, phone = ? "
                + "WHERE id = ?", r.departmentId(), code, r.designation(), r.phone(), id);
    }

    public boolean codeTaken(String code, long excludeId) {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM faculty WHERE employee_code = ? AND id <> ?",
                Integer.class, code, excludeId);
        return n != null && n > 0;
    }
}
