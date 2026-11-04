package com.college.result.repository;

import com.college.result.dto.ReportDtos.AdminDashboard;
import com.college.result.dto.ReportDtos.DepartmentSummaryRow;
import com.college.result.dto.ReportDtos.GradeDistributionRow;
import com.college.result.dto.ReportDtos.MarksStatusRow;
import com.college.result.dto.ReportDtos.SubjectAnalysisRow;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Aggregate queries - every figure is computed from stored records. */
@Repository
public class ReportRepository {

    private final JdbcTemplate jdbc;

    public ReportRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<DepartmentSummaryRow> departmentSummary(long sessionId) {
        return jdbc.query("SELECT d.id, d.code, d.name, COUNT(r.id) AS students, "
                        + "COALESCE(SUM(r.result_status = 'PASS'), 0) AS passed, "
                        + "COALESCE(SUM(r.result_status = 'FAIL'), 0) AS failed, AVG(r.sgpa) AS avg_sgpa "
                        + "FROM results r JOIN students st ON st.id = r.student_id "
                        + "JOIN departments d ON d.id = st.department_id WHERE r.examination_session_id = ? "
                        + "GROUP BY d.id, d.code, d.name ORDER BY d.name",
                (rs, i) -> {
                    long total = rs.getLong("students");
                    long passed = rs.getLong("passed");
                    return new DepartmentSummaryRow(rs.getLong("id"), rs.getString("code"), rs.getString("name"),
                            total, passed, rs.getLong("failed"), percent(passed, total),
                            scale(rs.getBigDecimal("avg_sgpa")));
                }, sessionId);
    }

    public List<SubjectAnalysisRow> subjectAnalysis(long sessionId, long departmentId) {
        return jdbc.query("SELECT rs.subject_code, rs.subject_name, COUNT(*) AS appeared, "
                        + "COALESCE(SUM(rs.passed), 0) AS passed, AVG(rs.total_marks) AS avg_total, "
                        + "MAX(rs.total_marks) AS highest, MAX(rs.max_marks) AS max_total "
                        + "FROM result_subjects rs JOIN results r ON r.id = rs.result_id "
                        + "JOIN students st ON st.id = r.student_id WHERE r.examination_session_id = ? "
                        + "AND (? = 0 OR st.department_id = ?) GROUP BY rs.subject_id, rs.subject_code, rs.subject_name "
                        + "ORDER BY rs.subject_code",
                (rs, i) -> {
                    long appeared = rs.getLong("appeared");
                    long passed = rs.getLong("passed");
                    return new SubjectAnalysisRow(rs.getString("subject_code"), rs.getString("subject_name"), appeared,
                            passed, appeared - passed, percent(passed, appeared), scale(rs.getBigDecimal("avg_total")),
                            rs.getBigDecimal("highest"), rs.getBigDecimal("max_total"));
                }, sessionId, departmentId, departmentId);
    }

    public List<GradeDistributionRow> gradeDistribution(long sessionId, long departmentId) {
        return jdbc.query("SELECT rs.grade, COUNT(*) AS cnt, MAX(rs.grade_point) AS gp FROM result_subjects rs "
                        + "JOIN results r ON r.id = rs.result_id JOIN students st ON st.id = r.student_id "
                        + "WHERE r.examination_session_id = ? AND (? = 0 OR st.department_id = ?) "
                        + "GROUP BY rs.grade ORDER BY gp DESC, rs.grade",
                (rs, i) -> new GradeDistributionRow(rs.getString("grade"), rs.getLong("cnt")),
                sessionId, departmentId, departmentId);
    }

    public List<MarksStatusRow> marksStatus(long sessionId) {
        return jdbc.query("SELECT d.code, d.name, COALESCE(SUM(m.status = 'DRAFT'), 0) AS draft, "
                        + "COALESCE(SUM(m.status = 'SUBMITTED'), 0) AS submitted, "
                        + "COALESCE(SUM(m.status = 'APPROVED'), 0) AS approved, "
                        + "COALESCE(SUM(m.status = 'REJECTED'), 0) AS rejected, "
                        + "COALESCE(SUM(m.status = 'PUBLISHED'), 0) AS published FROM marks m "
                        + "JOIN students st ON st.id = m.student_id JOIN departments d ON d.id = st.department_id "
                        + "WHERE m.examination_session_id = ? GROUP BY d.id, d.code, d.name ORDER BY d.name",
                (rs, i) -> new MarksStatusRow(rs.getString("code"), rs.getString("name"), rs.getLong("draft"),
                        rs.getLong("submitted"), rs.getLong("approved"), rs.getLong("rejected"),
                        rs.getLong("published")), sessionId);
    }

    public AdminDashboard dashboard() {
        return jdbc.queryForObject("SELECT "
                + "(SELECT COUNT(*) FROM students st JOIN users u ON u.id = st.user_id WHERE u.active = 1) AS students, "
                + "(SELECT COUNT(*) FROM faculty f JOIN users u ON u.id = f.user_id WHERE u.active = 1) AS faculty, "
                + "(SELECT COUNT(*) FROM departments WHERE active = 1) AS departments, "
                + "(SELECT COUNT(*) FROM subjects WHERE active = 1) AS subjects, "
                + "(SELECT COUNT(*) FROM examination_sessions WHERE status = 'OPEN') AS open_sessions, "
                + "(SELECT COUNT(*) FROM marks WHERE status = 'SUBMITTED') AS awaiting, "
                + "(SELECT COUNT(*) FROM results) AS published, "
                + "(SELECT name FROM grading_rules WHERE active = 1 LIMIT 1) AS rule",
                (rs, i) -> new AdminDashboard(rs.getLong("students"), rs.getLong("faculty"),
                        rs.getLong("departments"), rs.getLong("subjects"), rs.getLong("open_sessions"),
                        rs.getLong("awaiting"), rs.getLong("published"), rs.getString("rule")));
    }

    private static BigDecimal percent(long part, long total) {
        if (total == 0) {
            return BigDecimal.ZERO.setScale(2);
        }
        return BigDecimal.valueOf(part).multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP);
    }

    private static BigDecimal scale(BigDecimal v) {
        return v == null ? BigDecimal.ZERO.setScale(2) : v.setScale(2, RoundingMode.HALF_UP);
    }
}
