package com.college.result.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.college.result.support.AbstractIntegrationTest;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Draft -> submit -> review workflow, validation and faculty ownership through the real HTTP layer. */
class MarksWorkflowApiTest extends AbstractIntegrationTest {

    private static final String CS303 = "/api/faculty/assignments/9/sessions/3";   // fac.cse1
    private static final String CS302 = "/api/faculty/assignments/8/sessions/3";   // fac.cse2

    private String entries(String... students) {
        StringBuilder sb = new StringBuilder("{\"entries\":[");
        for (int i = 0; i < students.length; i++) {
            sb.append(i == 0 ? "" : ",").append(students[i]);
        }
        return sb.append("]}").toString();
    }

    private String entry(int student, String internal, String external) {
        return "{\"studentId\":" + student + ",\"internalMarks\":" + internal + ",\"externalMarks\":" + external + "}";
    }

    private String fullSheet() {
        return entries(entry(1, "30", "50"), entry(2, "28.5", "44.25"), entry(3, "35", "55"),
                entry(4, "20", "30"), entry(5, "39.99", "59.99"), entry(6, "25", "40"));
    }

    @Test
    void facultyCannotAccessOrChangeAnotherFacultysSubject() throws Exception {
        Session other = login("fac.cse2");
        getAs(other, CS303 + "/marks").andExpect(status().isForbidden());
        putAs(other, CS303 + "/marks", entries(entry(1, "10", "10"))).andExpect(status().isForbidden());
        postAs(other, CS303 + "/submit", null).andExpect(status().isForbidden());
        getAs(other, "/api/faculty/assignments/9/students").andExpect(status().isForbidden());
        assertThat(queryLong("SELECT COUNT(*) FROM marks WHERE subject_id = 9")).isZero();
    }

    @Test
    void facultyListsOnlyOwnAssignments() throws Exception {
        getAs(login("fac.cse1"), "/api/faculty/assignments").andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(6))   // CS101, CS103, CS201, CS203, CS301, CS303
                .andExpect(jsonPath("$.data[?(@.subjectCode=='CS302')]").isEmpty());
        getAs(login("fac.ece1"), "/api/faculty/assignments/10/students").andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(4));
    }

    @Test
    void marksAboveSubjectMaximumAreRejectedOnTheServer() throws Exception {
        Session f = login("fac.cse1");
        putAs(f, CS303 + "/marks", entries(entry(1, "41", "10"))).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("exceed the maximum of 40")));
        putAs(f, CS303 + "/marks", entries(entry(1, "10", "60.01"))).andExpect(status().isBadRequest());
        putAs(f, CS303 + "/marks", entries(entry(1, "-1", "10"))).andExpect(status().isBadRequest());
        putAs(f, CS303 + "/marks", entries(entry(1, "10.123", "10"))).andExpect(status().isBadRequest());
        assertThat(queryLong("SELECT COUNT(*) FROM marks WHERE subject_id = 9")).isZero();
        putAs(f, CS303 + "/marks", entries(entry(1, "40", "60"))).andExpect(status().isOk());
    }

    @Test
    void rejectsStudentsNotEnrolledInTheSubject() throws Exception {
        putAs(login("fac.cse1"), CS303 + "/marks", entries(entry(7, "10", "10"))).andExpect(status().isBadRequest());
    }

    @Test
    void draftSaveIsIdempotentAndNeverCreatesDuplicateRows() throws Exception {
        Session f = login("fac.cse1");
        putAs(f, CS303 + "/marks", entries(entry(1, "10", "20"))).andExpect(status().isOk());
        putAs(f, CS303 + "/marks", entries(entry(1, "11", "21"))).andExpect(status().isOk());
        assertThat(queryLong("SELECT COUNT(*) FROM marks WHERE student_id = 1 AND subject_id = 9")).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT internal_marks FROM marks WHERE student_id = 1 AND subject_id = 9",
                java.math.BigDecimal.class)).isEqualByComparingTo("11");
        // both writes were audited
        assertThat(queryLong("SELECT COUNT(*) FROM audit_logs WHERE action = 'MARKS_CREATED'")).isEqualTo(1);
        assertThat(queryLong("SELECT COUNT(*) FROM audit_logs WHERE action = 'MARKS_UPDATED'")).isEqualTo(1);
    }

    @Test
    void partialDraftsCanBeSavedButCannotBeSubmitted() throws Exception {
        Session f = login("fac.cse1");
        putAs(f, CS303 + "/marks", entries("{\"studentId\":1,\"internalMarks\":10}")).andExpect(status().isOk());
        postAs(f, CS303 + "/submit", null).andExpect(status().isConflict());
        assertThat(queryLong("SELECT COUNT(*) FROM marks WHERE subject_id = 9 AND status = 'SUBMITTED'")).isZero();
    }

    @Test
    void submitThenFacultyCanNoLongerEditUntilRejected() throws Exception {
        Session f = login("fac.cse1");
        putAs(f, CS303 + "/marks", fullSheet()).andExpect(status().isOk());
        postAs(f, CS303 + "/submit", null).andExpect(status().isOk()).andExpect(jsonPath("$.data.submitted").value(6));
        putAs(f, CS303 + "/marks", entries(entry(1, "1", "1"))).andExpect(status().isConflict());
        postAs(f, CS303 + "/submit", null).andExpect(status().isConflict()); // nothing left to submit
        assertThat(queryLong("SELECT COUNT(*) FROM audit_logs WHERE action = 'MARKS_SUBMITTED'")).isEqualTo(6);

        // reject one -> faculty may correct and resubmit that one only
        Session admin = login("admin");
        Long markId = queryLong("SELECT id FROM marks WHERE student_id = 1 AND subject_id = 9");
        postAs(admin, "/api/admin/marks/review", Map.of("markIds", java.util.List.of(markId), "action", "REJECT",
                "remarks", "Please verify")).andExpect(status().isOk());
        getAs(f, CS303 + "/marks").andExpect(jsonPath("$.data.rows[0].status").value("REJECTED"))
                .andExpect(jsonPath("$.data.rows[0].reviewRemarks").value("Please verify"))
                .andExpect(jsonPath("$.data.rows[0].editable").value(true))
                .andExpect(jsonPath("$.data.rows[1].editable").value(false));
        putAs(f, CS303 + "/marks", entries(entry(1, "31", "51"))).andExpect(status().isOk());
        postAs(f, CS303 + "/submit", null).andExpect(status().isOk()).andExpect(jsonPath("$.data.submitted").value(1));
    }

    @Test
    void closedExaminationSessionBlocksMarksEntry() throws Exception {
        jdbc.update("UPDATE examination_sessions SET status = 'CLOSED' WHERE id = 3");
        Session f = login("fac.cse1");
        putAs(f, CS303 + "/marks", entries(entry(1, "10", "10"))).andExpect(status().isConflict());
        getAs(f, CS303 + "/marks").andExpect(status().isOk()).andExpect(jsonPath("$.data.sessionOpen").value(false));
    }

    @Test
    void sessionFromAnotherSemesterIsRejected() throws Exception {
        putAs(login("fac.cse1"), "/api/faculty/assignments/9/sessions/1/marks", entries(entry(1, "10", "10")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void adminReviewRules() throws Exception {
        Session admin = login("admin");
        Long submitted = queryLong("SELECT id FROM marks WHERE status = 'SUBMITTED' ORDER BY id LIMIT 1");
        Long draft = queryLong("SELECT id FROM marks WHERE status = 'DRAFT' ORDER BY id LIMIT 1");
        postAs(admin, "/api/admin/marks/review", Map.of("markIds", java.util.List.of(submitted), "action", "REJECT"))
                .andExpect(status().isBadRequest());                                  // remarks required
        postAs(admin, "/api/admin/marks/review", Map.of("markIds", java.util.List.of(draft), "action", "APPROVE"))
                .andExpect(status().isConflict());                                    // only SUBMITTED
        postAs(admin, "/api/admin/marks/review", Map.of("markIds", java.util.List.of(submitted), "action", "MAYBE"))
                .andExpect(status().isBadRequest());
        postAs(admin, "/api/admin/marks/review", Map.of("markIds", java.util.List.of(submitted), "action", "APPROVE"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.reviewed").value(1));
        assertThat(jdbc.queryForObject("SELECT status FROM marks WHERE id = ?", String.class, submitted))
                .isEqualTo("APPROVED");
        assertThat(queryLong("SELECT COUNT(*) FROM audit_logs WHERE action = 'MARKS_APPROVED' AND entity_id = ?",
                submitted)).isEqualTo(1);
        // approved marks are locked for faculty
        putAs(login("fac.cse1"), "/api/faculty/assignments/7/sessions/3/marks", entries(entry(1, "1", "1")))
                .andExpect(status().isConflict());
    }

    @Test
    void reviewWithAnyInvalidMarkChangesNothing() throws Exception {
        Session admin = login("admin");
        Long submitted = queryLong("SELECT id FROM marks WHERE status = 'SUBMITTED' ORDER BY id LIMIT 1");
        Long draft = queryLong("SELECT id FROM marks WHERE status = 'DRAFT' ORDER BY id LIMIT 1");
        postAs(admin, "/api/admin/marks/review", Map.of("markIds", java.util.List.of(submitted, draft),
                "action", "APPROVE")).andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("SELECT status FROM marks WHERE id = ?", String.class, submitted))
                .isEqualTo("SUBMITTED");
    }

    @Test
    void marksListSupportsPaginationAndFilters() throws Exception {
        Session admin = login("admin");
        getAs(admin, "/api/admin/marks?status=SUBMITTED&size=4&page=0").andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(4))
                .andExpect(jsonPath("$.data.totalElements").value(6))
                .andExpect(jsonPath("$.data.totalPages").value(2));
        getAs(admin, "/api/admin/marks?status=BOGUS").andExpect(status().isBadRequest());
    }
}
