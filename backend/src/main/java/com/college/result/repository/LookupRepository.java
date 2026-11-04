package com.college.result.repository;

import com.college.result.dto.MasterDtos.Option;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/** Small id/label lists that feed drop-downs in the admin UI. */
@Repository
public class LookupRepository {

    private static final RowMapper<Option> OPTION = (rs, i) ->
            new Option(rs.getLong("id"), rs.getString("label"), null);
    private static final RowMapper<Option> OPTION_WITH_META = (rs, i) ->
            new Option(rs.getLong("id"), rs.getString("label"), rs.getInt("meta"));

    private final JdbcTemplate jdbc;

    public LookupRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<Option> departments() {
        return jdbc.query("SELECT id, CONCAT(code, ' - ', name) AS label FROM departments WHERE active = 1 ORDER BY name",
                OPTION);
    }

    public List<Option> academicYears() {
        return jdbc.query("SELECT id, label FROM academic_years ORDER BY start_date DESC", OPTION);
    }

    public List<Option> semesters() {
        return jdbc.query("SELECT s.id, CONCAT(ay.label, ' - ', s.name) AS label, s.semester_number AS meta FROM semesters s "
                + "JOIN academic_years ay ON ay.id = s.academic_year_id ORDER BY ay.start_date DESC, s.semester_number",
                OPTION_WITH_META);
    }

    public List<Option> subjects(long departmentId, int semesterNumber) {
        return jdbc.query("SELECT id, CONCAT(code, ' - ', name) AS label, semester_number AS meta FROM subjects WHERE active = 1 "
                + "AND (? = 0 OR department_id = ?) AND (? = 0 OR semester_number = ?) ORDER BY code", OPTION_WITH_META,
                departmentId, departmentId, semesterNumber, semesterNumber);
    }

    public List<Option> faculty(long departmentId) {
        return jdbc.query("SELECT f.id, CONCAT(u.full_name, ' (', f.employee_code, ')') AS label FROM faculty f "
                + "JOIN users u ON u.id = f.user_id WHERE u.active = 1 AND (? = 0 OR f.department_id = ?) "
                + "ORDER BY u.full_name", OPTION, departmentId, departmentId);
    }

    public List<Option> students(long departmentId) {
        return jdbc.query("SELECT st.id, CONCAT(st.roll_number, ' - ', u.full_name) AS label FROM students st "
                + "JOIN users u ON u.id = st.user_id WHERE u.active = 1 AND (? = 0 OR st.department_id = ?) "
                + "ORDER BY st.roll_number LIMIT 1000", OPTION, departmentId, departmentId);
    }

    public List<Option> examSessions() {
        return jdbc.query("SELECT es.id, CONCAT(es.name, ' (', ay.label, ' / Sem ', sem.semester_number, ')') AS label "
                + "FROM examination_sessions es JOIN semesters sem ON sem.id = es.semester_id "
                + "JOIN academic_years ay ON ay.id = sem.academic_year_id ORDER BY es.start_date DESC, es.id DESC",
                OPTION);
    }
}
