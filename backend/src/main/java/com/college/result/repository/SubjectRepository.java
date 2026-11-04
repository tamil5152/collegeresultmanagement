package com.college.result.repository;

import com.college.result.common.DbUtil;
import com.college.result.common.Paging;
import com.college.result.dto.MasterDtos.SubjectRequest;
import com.college.result.dto.MasterDtos.SubjectResponse;
import com.college.result.model.Subject;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class SubjectRepository {

    private static final String SELECT = "SELECT s.id, s.department_id, d.code AS dept_code, s.code, s.name, "
            + "s.semester_number, s.credits, s.internal_max, s.external_max, s.internal_pass, s.external_pass, "
            + "s.total_pass, s.active FROM subjects s JOIN departments d ON d.id = s.department_id ";
    private static final String FILTER = "WHERE (s.code LIKE ? OR s.name LIKE ?) AND (? = 0 OR s.department_id = ?) "
            + "AND (? = 0 OR s.semester_number = ?) ";
    private static final RowMapper<SubjectResponse> MAPPER = (rs, i) -> new SubjectResponse(rs.getLong("id"),
            rs.getLong("department_id"), rs.getString("dept_code"), rs.getString("code"), rs.getString("name"),
            rs.getInt("semester_number"), rs.getBigDecimal("credits"), rs.getBigDecimal("internal_max"),
            rs.getBigDecimal("external_max"), rs.getBigDecimal("internal_pass"), rs.getBigDecimal("external_pass"),
            rs.getBigDecimal("total_pass"), rs.getBoolean("active"));
    private static final RowMapper<Subject> MODEL = (rs, i) -> new Subject(rs.getLong("id"),
            rs.getLong("department_id"), rs.getString("code"), rs.getString("name"), rs.getInt("semester_number"),
            rs.getBigDecimal("credits"), rs.getBigDecimal("internal_max"), rs.getBigDecimal("external_max"),
            rs.getBigDecimal("internal_pass"), rs.getBigDecimal("external_pass"), rs.getBigDecimal("total_pass"),
            rs.getBoolean("active"));

    private final JdbcTemplate jdbc;

    public SubjectRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<SubjectResponse> search(String search, long departmentId, int semesterNumber, Paging p) {
        String like = Paging.like(search);
        return jdbc.query(SELECT + FILTER + "ORDER BY d.code, s.semester_number, s.code LIMIT ? OFFSET ?", MAPPER,
                like, like, departmentId, departmentId, semesterNumber, semesterNumber, p.size(), p.offset());
    }

    public long count(String search, long departmentId, int semesterNumber) {
        String like = Paging.like(search);
        Long n = jdbc.queryForObject("SELECT COUNT(*) FROM subjects s " + FILTER, Long.class, like, like,
                departmentId, departmentId, semesterNumber, semesterNumber);
        return n == null ? 0 : n;
    }

    public Optional<SubjectResponse> findResponse(long id) {
        return jdbc.query(SELECT + "WHERE s.id = ?", MAPPER, id).stream().findFirst();
    }

    public Optional<Subject> findById(long id) {
        return jdbc.query("SELECT * FROM subjects WHERE id = ?", MODEL, id).stream().findFirst();
    }

    public long insert(SubjectRequest r) {
        return DbUtil.insert(jdbc, "INSERT INTO subjects (department_id, code, name, semester_number, credits, "
                        + "internal_max, external_max, internal_pass, external_pass, total_pass) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)", r.departmentId(), r.code(), r.name(),
                r.semesterNumber(), r.credits(), r.internalMax(), r.externalMax(), r.internalPass(),
                r.externalPass(), r.totalPass());
    }

    public int update(long id, SubjectRequest r) {
        return jdbc.update("UPDATE subjects SET department_id = ?, code = ?, name = ?, semester_number = ?, "
                        + "credits = ?, internal_max = ?, external_max = ?, internal_pass = ?, external_pass = ?, "
                        + "total_pass = ? WHERE id = ?", r.departmentId(), r.code(), r.name(), r.semesterNumber(),
                r.credits(), r.internalMax(), r.externalMax(), r.internalPass(), r.externalPass(), r.totalPass(), id);
    }

    public int setActive(long id, boolean active) {
        return jdbc.update("UPDATE subjects SET active = ? WHERE id = ?", active, id);
    }

    public boolean hasMarks(long subjectId) {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM marks WHERE subject_id = ?", Integer.class, subjectId);
        return n != null && n > 0;
    }
}
