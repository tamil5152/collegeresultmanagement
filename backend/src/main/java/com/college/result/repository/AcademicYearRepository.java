package com.college.result.repository;

import com.college.result.common.DbUtil;
import com.college.result.common.Paging;
import com.college.result.dto.MasterDtos.AcademicYearResponse;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class AcademicYearRepository {

    private static final RowMapper<AcademicYearResponse> MAPPER = (rs, i) -> new AcademicYearResponse(rs.getLong("id"),
            rs.getString("label"), DbUtil.date(rs, "start_date"), DbUtil.date(rs, "end_date"), rs.getBoolean("active"));

    private final JdbcTemplate jdbc;

    public AcademicYearRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<AcademicYearResponse> search(String search, Paging p) {
        return jdbc.query("SELECT id, label, start_date, end_date, active FROM academic_years WHERE label LIKE ? "
                + "ORDER BY start_date DESC LIMIT ? OFFSET ?", MAPPER, Paging.like(search), p.size(), p.offset());
    }

    public long count(String search) {
        Long n = jdbc.queryForObject("SELECT COUNT(*) FROM academic_years WHERE label LIKE ?", Long.class,
                Paging.like(search));
        return n == null ? 0 : n;
    }

    public Optional<AcademicYearResponse> findById(long id) {
        return jdbc.query("SELECT id, label, start_date, end_date, active FROM academic_years WHERE id = ?", MAPPER, id)
                .stream().findFirst();
    }

    public long insert(String label, LocalDate start, LocalDate end) {
        return DbUtil.insert(jdbc, "INSERT INTO academic_years (label, start_date, end_date) VALUES (?, ?, ?)",
                label, start, end);
    }

    public int update(long id, String label, LocalDate start, LocalDate end) {
        return jdbc.update("UPDATE academic_years SET label = ?, start_date = ?, end_date = ? WHERE id = ?",
                label, start, end, id);
    }

    public int setActive(long id, boolean active) {
        return jdbc.update("UPDATE academic_years SET active = ? WHERE id = ?", active, id);
    }
}
