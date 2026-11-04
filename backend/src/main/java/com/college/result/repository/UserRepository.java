package com.college.result.repository;

import com.college.result.common.DbUtil;
import com.college.result.model.Role;
import com.college.result.model.User;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class UserRepository {

    private static final String COLS = "id, username, email, password_hash, full_name, role, active";
    private static final RowMapper<User> MAPPER = (rs, i) -> new User(rs.getLong("id"), rs.getString("username"),
            rs.getString("email"), rs.getString("password_hash"), rs.getString("full_name"),
            Role.valueOf(rs.getString("role")), rs.getBoolean("active"));

    private final JdbcTemplate jdbc;

    public UserRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<User> findById(long id) {
        return jdbc.query("SELECT " + COLS + " FROM users WHERE id = ?", MAPPER, id).stream().findFirst();
    }

    public Optional<User> findByUsername(String username) {
        return jdbc.query("SELECT " + COLS + " FROM users WHERE username = ?", MAPPER, username).stream().findFirst();
    }

    public long insert(String username, String email, String passwordHash, String fullName, Role role) {
        return DbUtil.insert(jdbc, "INSERT INTO users (username, email, password_hash, full_name, role, active) "
                + "VALUES (?, ?, ?, ?, ?, 1)", username, email, passwordHash, fullName, role.name());
    }

    public void updateProfile(long id, String fullName, String email) {
        jdbc.update("UPDATE users SET full_name = ?, email = ? WHERE id = ?", fullName, email, id);
    }

    public void updatePassword(long id, String passwordHash) {
        jdbc.update("UPDATE users SET password_hash = ? WHERE id = ?", passwordHash, id);
    }

    public void setActive(long id, boolean active) {
        jdbc.update("UPDATE users SET active = ? WHERE id = ?", active, id);
    }

    public void touchLastLogin(long id) {
        jdbc.update("UPDATE users SET last_login_at = NOW() WHERE id = ?", id);
    }

    public boolean existsAdmin() {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE role = 'ADMIN'", Integer.class);
        return n != null && n > 0;
    }

    public boolean usernameTaken(String username) {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE username = ?", Integer.class, username);
        return n != null && n > 0;
    }

    public boolean emailTaken(String email, long excludeUserId) {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE email = ? AND id <> ?", Integer.class,
                email, excludeUserId);
        return n != null && n > 0;
    }
}
