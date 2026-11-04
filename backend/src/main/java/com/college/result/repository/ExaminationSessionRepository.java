package com.college.result.repository;

import com.college.result.common.DbUtil;
import com.college.result.common.Paging;
import com.college.result.dto.MasterDtos.ExamSessionRequest;
import com.college.result.dto.MasterDtos.ExamSessionResponse;
import com.college.result.model.ExaminationSession;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class ExaminationSessionRepository {

    private static final String SELECT = "SELECT es.id, es.name, es.semester_id, sem.name AS sem_name, "
            + "sem.semester_number, ay.label AS year_label, es.start_date, es.end_date, es.status "
            + "FROM examination_sessions es JOIN semesters sem ON sem.id = es.semester_id "
            + "JOIN academic_years ay ON ay.id = sem.academic_year_id ";
    private static final RowMapper<ExamSessionResponse> MAPPER = (rs, i) -> new ExamSessionResponse(rs.getLong("id"),
            rs.getString("name"), rs.getLong("semester_id"), rs.getString("sem_name"), rs.getString("year_label"),
            rs.getInt("semester_number"), DbUtil.date(rs, "start_date"), DbUtil.date(rs, "end_date"),
            rs.getString("status"));
    private static final RowMapper<ExaminationSession> MODEL = (rs, i) -> new ExaminationSession(rs.getLong("id"),
            rs.getString("name"), rs.getLong("semester_id"), rs.getInt("semester_number"),
            DbUtil.date(rs, "start_date"), DbUtil.date(rs, "end_date"), rs.getString("status"));

    private final JdbcTemplate jdbc;

    public ExaminationSessionRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<ExamSessionResponse> search(String search, Paging p) {
        String like = Paging.like(search);
        return jdbc.query(SELECT + "WHERE es.name LIKE ? OR sem.name LIKE ? OR ay.label LIKE ? "
                + "ORDER BY es.start_date DESC, es.id DESC LIMIT ? OFFSET ?", MAPPER, like, like, like, p.size(),
                p.offset());
    }

    public long count(String search) {
        String like = Paging.like(search);
        Long n = jdbc.queryForObject("SELECT COUNT(*) FROM examination_sessions es JOIN semesters sem "
                + "ON sem.id = es.semester_id JOIN academic_years ay ON ay.id = sem.academic_year_id "
                + "WHERE es.name LIKE ? OR sem.name LIKE ? OR ay.label LIKE ?", Long.class, like, like, like);
        return n == null ? 0 : n;
    }

    public Optional<ExamSessionResponse> findResponse(long id) {
        return jdbc.query(SELECT + "WHERE es.id = ?", MAPPER, id).stream().findFirst();
    }

    public Optional<ExaminationSession> findById(long id) {
        return jdbc.query("SELECT es.id, es.name, es.semester_id, sem.semester_number, es.start_date, es.end_date, "
                + "es.status FROM examination_sessions es JOIN semesters sem ON sem.id = es.semester_id "
                + "WHERE es.id = ?", MODEL, id).stream().findFirst();
    }

    /** Row lock used to serialise publication / correction for one session. */
    public Optional<ExaminationSession> lockById(long id) {
        return jdbc.query("SELECT es.id, es.name, es.semester_id, sem.semester_number, es.start_date, es.end_date, "
                + "es.status FROM examination_sessions es JOIN semesters sem ON sem.id = es.semester_id "
                + "WHERE es.id = ? FOR UPDATE", MODEL, id).stream().findFirst();
    }

    public List<ExaminationSession> findBySemester(long semesterId) {
        return jdbc.query("SELECT es.id, es.name, es.semester_id, sem.semester_number, es.start_date, es.end_date, "
                + "es.status FROM examination_sessions es JOIN semesters sem ON sem.id = es.semester_id "
                + "WHERE es.semester_id = ? ORDER BY es.start_date, es.id", MODEL, semesterId);
    }

    public long insert(ExamSessionRequest r) {
        return DbUtil.insert(jdbc, "INSERT INTO examination_sessions (name, semester_id, start_date, end_date, status) "
                + "VALUES (?, ?, ?, ?, ?)", r.name(), r.semesterId(), r.startDate(), r.endDate(), r.status());
    }

    public int update(long id, ExamSessionRequest r) {
        return jdbc.update("UPDATE examination_sessions SET name = ?, semester_id = ?, start_date = ?, end_date = ?, "
                + "status = ? WHERE id = ?", r.name(), r.semesterId(), r.startDate(), r.endDate(), r.status(), id);
    }

    public boolean hasMarks(long sessionId) {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM marks WHERE examination_session_id = ?", Integer.class,
                sessionId);
        return n != null && n > 0;
    }
}
