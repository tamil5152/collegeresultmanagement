package com.college.result.repository;

import com.college.result.common.DbUtil;
import com.college.result.common.Paging;
import com.college.result.dto.MasterDtos.SemesterResponse;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class SemesterRepository {

    private static final String SELECT = "SELECT s.id, s.academic_year_id, ay.label AS year_label, s.semester_number, "
            + "s.name, s.start_date, s.end_date FROM semesters s JOIN academic_years ay ON ay.id = s.academic_year_id ";
    private static final RowMapper<SemesterResponse> MAPPER = (rs, i) -> new SemesterResponse(rs.getLong("id"),
            rs.getLong("academic_year_id"), rs.getString("year_label"), rs.getInt("semester_number"),
            rs.getString("name"), DbUtil.date(rs, "start_date"), DbUtil.date(rs, "end_date"));

    private final JdbcTemplate jdbc;

    public SemesterRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<SemesterResponse> search(String search, Paging p) {
        String like = Paging.like(search);
        return jdbc.query(SELECT + "WHERE s.name LIKE ? OR ay.label LIKE ? ORDER BY ay.start_date DESC, "
                + "s.semester_number LIMIT ? OFFSET ?", MAPPER, like, like, p.size(), p.offset());
    }

    public long count(String search) {
        String like = Paging.like(search);
        Long n = jdbc.queryForObject("SELECT COUNT(*) FROM semesters s JOIN academic_years ay "
                + "ON ay.id = s.academic_year_id WHERE s.name LIKE ? OR ay.label LIKE ?", Long.class, like, like);
        return n == null ? 0 : n;
    }

    public Optional<SemesterResponse> findById(long id) {
        return jdbc.query(SELECT + "WHERE s.id = ?", MAPPER, id).stream().findFirst();
    }

    public long insert(long yearId, int number, String name, LocalDate start, LocalDate end) {
        return DbUtil.insert(jdbc, "INSERT INTO semesters (academic_year_id, semester_number, name, start_date, "
                + "end_date) VALUES (?, ?, ?, ?, ?)", yearId, number, name, start, end);
    }

    public int update(long id, long yearId, int number, String name, LocalDate start, LocalDate end) {
        return jdbc.update("UPDATE semesters SET academic_year_id = ?, semester_number = ?, name = ?, start_date = ?, "
                + "end_date = ? WHERE id = ?", yearId, number, name, start, end, id);
    }
}
