package com.college.result.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.college.result.support.AbstractIntegrationTest;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class ResultPublicationApiTest extends AbstractIntegrationTest {

    private static final String PUBLISH = "/api/admin/results/publish";

    @Test
    void publishesApprovedResultsWithCorrectCalculations() throws Exception {
        Session admin = login("admin");
        getAs(admin, "/api/admin/results/publication-status?sessionId=1&departmentId=1").andExpect(status().isOk())
                .andExpect(jsonPath("$.data.canPublish").value(true))
                .andExpect(jsonPath("$.data.studentsToPublish").value(6));
        postAs(admin, PUBLISH, "{\"sessionId\":1,\"departmentId\":1}").andExpect(status().isOk())
                .andExpect(jsonPath("$.data.published").value(6));

        // Student 1, semester 1: CS101 71%=A(8) 4cr, CS102 79%=A(8) 4cr, CS103 87%=A+(9) 3cr -> 91/11 = 8.27
        assertThat(jdbc.queryForObject("SELECT sgpa FROM results WHERE student_id = 1 AND examination_session_id = 1",
                BigDecimal.class)).isEqualByComparingTo("8.27");
        // Student 6 fails CS102 (external 20 < pass mark 24): F, 0 points, SGPA includes the failed credits
        assertThat(jdbc.queryForObject("SELECT result_status FROM results WHERE student_id = 6 "
                + "AND examination_session_id = 1", String.class)).isEqualTo("FAIL");
        assertThat(jdbc.queryForObject("SELECT grade FROM result_subjects rs JOIN results r ON r.id = rs.result_id "
                + "WHERE r.student_id = 6 AND r.examination_session_id = 1 AND rs.subject_code = 'CS102'",
                String.class)).isEqualTo("F");
        // marks moved to PUBLISHED and the publication is audited
        assertThat(queryLong("SELECT COUNT(*) FROM marks WHERE examination_session_id = 1 AND status = 'PUBLISHED'"))
                .isEqualTo(18);
        assertThat(queryLong("SELECT COUNT(*) FROM audit_logs WHERE action = 'RESULT_PUBLISHED'")).isEqualTo(6);
        assertThat(queryLong("SELECT COUNT(*) FROM audit_logs WHERE action = 'RESULTS_PUBLISHED_BATCH'")).isEqualTo(1);
    }

    @Test
    void cannotPublishWhileMarksAreMissingOrUnapproved() throws Exception {
        Session admin = login("admin");
        getAs(admin, "/api/admin/results/publication-status?sessionId=3&departmentId=1").andExpect(status().isOk())
                .andExpect(jsonPath("$.data.canPublish").value(false))
                .andExpect(jsonPath("$.data.studentsWithMissingMarks").value(6));
        postAs(admin, PUBLISH, "{\"sessionId\":3,\"departmentId\":1}").andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("missing or unapproved")));
        assertThat(queryLong("SELECT COUNT(*) FROM results")).isZero();
        assertThat(queryLong("SELECT COUNT(*) FROM marks WHERE status = 'PUBLISHED'")).isZero();
    }

    @Test
    void oneUnapprovedMarkBlocksTheWholePublicationAtomically() throws Exception {
        jdbc.update("UPDATE marks SET status = 'SUBMITTED' WHERE student_id = 3 AND subject_id = 2 "
                + "AND examination_session_id = 1");
        Session admin = login("admin");
        postAs(admin, PUBLISH, "{\"sessionId\":1,\"departmentId\":1}").andExpect(status().isConflict());
        assertThat(queryLong("SELECT COUNT(*) FROM results")).isZero();
        getAs(admin, "/api/admin/results/publication-status?sessionId=1&departmentId=1")
                .andExpect(jsonPath("$.data.studentsWithUnapprovedMarks").value(1))
                .andExpect(jsonPath("$.data.readyStudents").value(5));
    }

    @Test
    void publishingRequiresAnActiveGradingRule() throws Exception {
        jdbc.update("UPDATE grading_rules SET active = 0");
        postAs(login("admin"), PUBLISH, "{\"sessionId\":1,\"departmentId\":1}").andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("grading rule")));
    }

    @Test
    void secondPublicationOfTheSameScopeIsRejected() throws Exception {
        Session admin = login("admin");
        postAs(admin, PUBLISH, "{\"sessionId\":1,\"departmentId\":1}").andExpect(status().isOk());
        postAs(admin, PUBLISH, "{\"sessionId\":1,\"departmentId\":1}").andExpect(status().isConflict());
        assertThat(queryLong("SELECT COUNT(*) FROM results")).isEqualTo(6);
    }

    @Test
    void validatesRequestBody() throws Exception {
        postAs(login("admin"), PUBLISH, "{\"sessionId\":1}").andExpect(status().isBadRequest());
        postAs(login("admin"), PUBLISH, "{\"sessionId\":999,\"departmentId\":1}").andExpect(status().isNotFound());
    }

    @Test
    void cgpaIsCumulativeAcrossPublishedSemesters() throws Exception {
        Session admin = login("admin");
        publishHistoricResults(admin);
        // Student 1: sem1 91/11 grade-point-credits, sem2 73/10 -> CGPA 164/21 = 7.81
        getAs(login("cs2024001"), "/api/student/results").andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].sgpa").value(7.3))
                .andExpect(jsonPath("$.data[0].cgpa").value(7.81))
                .andExpect(jsonPath("$.data[1].sgpa").value(8.27))
                .andExpect(jsonPath("$.data[1].cgpa").value(8.27));
        getAs(login("cs2024001"), "/api/student/dashboard").andExpect(jsonPath("$.data.cgpa").value(7.81))
                .andExpect(jsonPath("$.data.publishedResults").value(2));
    }

    @Test
    void publishedMarksCanOnlyBeChangedThroughTheAuditedCorrectionWorkflow() throws Exception {
        Session admin = login("admin");
        postAs(admin, PUBLISH, "{\"sessionId\":1,\"departmentId\":1}").andExpect(status().isOk());
        Long markId = queryLong("SELECT id FROM marks WHERE student_id = 1 AND subject_id = 1 "
                + "AND examination_session_id = 1");

        // faculty cannot silently edit a published mark (session closed AND status PUBLISHED)
        putAs(login("fac.cse1"), "/api/faculty/assignments/1/sessions/1/marks",
                "{\"entries\":[{\"studentId\":1,\"internalMarks\":40,\"externalMarks\":60}]}")
                .andExpect(status().isConflict());
        // even with an open session the PUBLISHED status itself blocks faculty edits
        jdbc.update("UPDATE examination_sessions SET status = 'OPEN' WHERE id = 1");
        putAs(login("fac.cse1"), "/api/faculty/assignments/1/sessions/1/marks",
                "{\"entries\":[{\"studentId\":1,\"internalMarks\":40,\"externalMarks\":60}]}")
                .andExpect(status().isConflict());

        // correction needs a reason and respects the subject maximum
        postAs(admin, "/api/admin/marks/" + markId + "/correct",
                "{\"internalMarks\":40,\"externalMarks\":60,\"reason\":\"short\"}").andExpect(status().isBadRequest());
        postAs(admin, "/api/admin/marks/" + markId + "/correct",
                "{\"internalMarks\":41,\"externalMarks\":60,\"reason\":\"Valid reason given here\"}")
                .andExpect(status().isBadRequest());
        postAs(admin, "/api/admin/marks/" + markId + "/correct",
                "{\"internalMarks\":30,\"externalMarks\":41,\"reason\":\"Same as original values\"}")
                .andExpect(status().isBadRequest());

        postAs(admin, "/api/admin/marks/" + markId + "/correct",
                "{\"internalMarks\":40,\"externalMarks\":60,\"reason\":\"Re-evaluation outcome ref 2024/77\"}")
                .andExpect(status().isOk());

        // history is kept, result recomputed (A 71% -> O 100%) and re-versioned
        assertThat(queryLong("SELECT COUNT(*) FROM mark_corrections WHERE mark_id = ?", markId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT old_internal FROM mark_corrections WHERE mark_id = ?",
                BigDecimal.class, markId)).isEqualByComparingTo("30");
        assertThat(jdbc.queryForObject("SELECT revision FROM results WHERE student_id = 1 "
                + "AND examination_session_id = 1", Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT grade FROM result_subjects rs JOIN results r ON r.id = rs.result_id "
                + "WHERE r.student_id = 1 AND r.examination_session_id = 1 AND rs.subject_code = 'CS101'",
                String.class)).isEqualTo("O");
        assertThat(jdbc.queryForObject("SELECT sgpa FROM results WHERE student_id = 1 AND examination_session_id = 1",
                BigDecimal.class)).isEqualByComparingTo("9.00"); // (10*4 + 8*4 + 9*3) / 11 = 99/11
        assertThat(queryLong("SELECT COUNT(*) FROM audit_logs WHERE action = 'MARKS_CORRECTED' AND entity_id = ?",
                markId)).isEqualTo(1);
        assertThat(queryLong("SELECT COUNT(*) FROM audit_logs WHERE action = 'RESULT_REVISED'")).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT details FROM audit_logs WHERE action = 'MARKS_CORRECTED'", String.class))
                .contains("Re-evaluation outcome").contains("\"oldInternal\":30");
    }

    @Test
    void correctionIsNotAvailableForMarksStillUnderReview() throws Exception {
        Long draft = queryLong("SELECT id FROM marks WHERE status = 'DRAFT' LIMIT 1");
        postAs(login("admin"), "/api/admin/marks/" + draft + "/correct",
                "{\"internalMarks\":10,\"externalMarks\":10,\"reason\":\"Trying to bypass review\"}")
                .andExpect(status().isConflict());
    }

    @Test
    void gradingRuleUsedByPublishedResultsCannotBeEdited() throws Exception {
        Session admin = login("admin");
        postAs(admin, PUBLISH, "{\"sessionId\":1,\"departmentId\":1}").andExpect(status().isOk());
        putAs(admin, "/api/admin/grading-rules/1", "{\"name\":\"Changed\",\"samplePlaceholder\":true,"
                + "\"includeFailedInGpa\":true,\"bands\":[{\"grade\":\"P\",\"minPercentage\":40,\"gradePoint\":4,"
                + "\"failGrade\":false},{\"grade\":\"F\",\"minPercentage\":0,\"gradePoint\":0,\"failGrade\":true}]}")
                .andExpect(status().isConflict());
    }

    @Test
    void gradingRuleValidationAndActivation() throws Exception {
        Session admin = login("admin");
        String noFail = "{\"name\":\"X\",\"samplePlaceholder\":false,\"includeFailedInGpa\":true,\"bands\":["
                + "{\"grade\":\"P\",\"minPercentage\":40,\"gradePoint\":4,\"failGrade\":false}]}";
        postAs(admin, "/api/admin/grading-rules", noFail).andExpect(status().isBadRequest());
        String ok = "{\"name\":\"Institution rule\",\"samplePlaceholder\":false,\"includeFailedInGpa\":false,\"bands\":["
                + "{\"grade\":\"P\",\"minPercentage\":40,\"gradePoint\":4,\"failGrade\":false},"
                + "{\"grade\":\"F\",\"minPercentage\":0,\"gradePoint\":0,\"failGrade\":true}]}";
        postAs(admin, "/api/admin/grading-rules", ok).andExpect(status().isCreated());
        Long id = queryLong("SELECT id FROM grading_rules WHERE name = 'Institution rule'");
        postAs(admin, "/api/admin/grading-rules/" + id + "/activate", null).andExpect(status().isOk());
        assertThat(queryLong("SELECT COUNT(*) FROM grading_rules WHERE active = 1")).isEqualTo(1);
        assertThat(queryLong("SELECT id FROM grading_rules WHERE active = 1")).isEqualTo(id);
    }
}
