package com.college.result.repository;

import com.college.result.common.DbUtil;
import com.college.result.common.Paging;
import com.college.result.dto.MasterDtos.DepartmentResponse;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class DepartmentRepository {

    private static final RowMapper<DepartmentResponse> MAPPER = (rs, i) -> new DepartmentResponse(rs.getLong("id"),
            rs.getString("code"), rs.getString("name"), rs.getBoolean("active"));

    private final JdbcTemplate jdbc;

    public DepartmentRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<DepartmentResponse> search(String search, Paging p) {
        String like = Paging.like(search);
        return jdbc.query("SELECT id, code, name, active FROM departments WHERE code LIKE ? OR name LIKE ? "
                + "ORDER BY name LIMIT ? OFFSET ?", MAPPER, like, like, p.size(), p.offset());
    }

    public long count(String search) {
        String like = Paging.like(search);
        Long n = jdbc.queryForObject("SELECT COUNT(*) FROM departments WHERE code LIKE ? OR name LIKE ?", Long.class,
                like, like);
        return n == null ? 0 : n;
    }

    public Optional<DepartmentResponse> findById(long id) {
        return jdbc.query("SELECT id, code, name, active FROM departments WHERE id = ?", MAPPER, id).stream().findFirst();
    }

    public long insert(String code, String name) {
        return DbUtil.insert(jdbc, "INSERT INTO departments (code, name) VALUES (?, ?)", code, name);
    }

    public int update(long id, String code, String name) {
        return jdbc.update("UPDATE departments SET code = ?, name = ? WHERE id = ?", code, name, id);
    }

    public int setActive(long id, boolean active) {
        return jdbc.update("UPDATE departments SET active = ? WHERE id = ?", active, id);
    }
}
