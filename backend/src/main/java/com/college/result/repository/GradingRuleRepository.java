package com.college.result.repository;

import com.college.result.common.DbUtil;
import com.college.result.dto.MasterDtos.BandRequest;
import com.college.result.dto.MasterDtos.BandResponse;
import com.college.result.dto.MasterDtos.GradingRuleResponse;
import com.college.result.model.GradeBand;
import com.college.result.model.GradingRule;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class GradingRuleRepository {

    private final JdbcTemplate jdbc;

    public GradingRuleRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private List<BandResponse> bands(long ruleId) {
        return jdbc.query("SELECT id, grade, min_percentage, grade_point, fail_grade FROM grading_rule_bands "
                        + "WHERE grading_rule_id = ? ORDER BY min_percentage DESC", (rs, i) -> new BandResponse(
                        rs.getLong("id"), rs.getString("grade"), rs.getBigDecimal("min_percentage"),
                        rs.getBigDecimal("grade_point"), rs.getBoolean("fail_grade")), ruleId);
    }

    public List<GradingRuleResponse> findAll() {
        return jdbc.query("SELECT r.id, r.name, r.description, r.sample_placeholder, r.include_failed_in_gpa, r.active, "
                        + "EXISTS (SELECT 1 FROM results x WHERE x.grading_rule_id = r.id) AS used "
                        + "FROM grading_rules r ORDER BY r.active DESC, r.name",
                (rs, i) -> new GradingRuleResponse(rs.getLong("id"), rs.getString("name"),
                        rs.getString("description"), rs.getBoolean("sample_placeholder"),
                        rs.getBoolean("include_failed_in_gpa"), rs.getBoolean("active"), rs.getBoolean("used"),
                        bands(rs.getLong("id"))));
    }

    public Optional<GradingRuleResponse> findResponse(long id) {
        return findAll().stream().filter(r -> r.id() == id).findFirst();
    }

    public Optional<GradingRule> findById(long id) {
        return jdbc.query("SELECT id, name, include_failed_in_gpa, active FROM grading_rules WHERE id = ?",
                (rs, i) -> new GradingRule(rs.getLong("id"), rs.getString("name"),
                        rs.getBoolean("include_failed_in_gpa"), rs.getBoolean("active"), modelBands(rs.getLong("id"))),
                id).stream().findFirst();
    }

    public Optional<GradingRule> findActive() {
        return jdbc.query("SELECT id, name, include_failed_in_gpa, active FROM grading_rules WHERE active = 1",
                (rs, i) -> new GradingRule(rs.getLong("id"), rs.getString("name"),
                        rs.getBoolean("include_failed_in_gpa"), rs.getBoolean("active"), modelBands(rs.getLong("id"))))
                .stream().findFirst();
    }

    private List<GradeBand> modelBands(long ruleId) {
        return jdbc.query("SELECT grade, min_percentage, grade_point, fail_grade FROM grading_rule_bands "
                        + "WHERE grading_rule_id = ?", (rs, i) -> new GradeBand(rs.getString("grade"),
                        rs.getBigDecimal("min_percentage"), rs.getBigDecimal("grade_point"),
                        rs.getBoolean("fail_grade")), ruleId);
    }

    public long insert(String name, String description, boolean sample, boolean includeFailed) {
        return DbUtil.insert(jdbc, "INSERT INTO grading_rules (name, description, sample_placeholder, "
                + "include_failed_in_gpa, active) VALUES (?, ?, ?, ?, 0)", name, description, sample, includeFailed);
    }

    public int update(long id, String name, String description, boolean sample, boolean includeFailed) {
        return jdbc.update("UPDATE grading_rules SET name = ?, description = ?, sample_placeholder = ?, "
                + "include_failed_in_gpa = ? WHERE id = ?", name, description, sample, includeFailed, id);
    }

    public void replaceBands(long ruleId, List<BandRequest> bands) {
        jdbc.update("DELETE FROM grading_rule_bands WHERE grading_rule_id = ?", ruleId);
        for (BandRequest b : bands) {
            jdbc.update("INSERT INTO grading_rule_bands (grading_rule_id, grade, min_percentage, grade_point, "
                    + "fail_grade) VALUES (?, ?, ?, ?, ?)", ruleId, b.grade().trim(), b.minPercentage(),
                    b.gradePoint(), b.failGrade());
        }
    }

    public void deactivateAll() {
        jdbc.update("UPDATE grading_rules SET active = 0 WHERE active = 1");
    }

    public int activate(long id) {
        return jdbc.update("UPDATE grading_rules SET active = 1 WHERE id = ?", id);
    }

    public boolean usedByResults(long id) {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM results WHERE grading_rule_id = ?", Integer.class, id);
        return n != null && n > 0;
    }
}
