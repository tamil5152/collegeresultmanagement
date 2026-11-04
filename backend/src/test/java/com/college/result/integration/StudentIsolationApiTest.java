package com.college.result.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.college.result.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/** Students must only ever see their own, published results. */
class StudentIsolationApiTest extends AbstractIntegrationTest {

    private Long resultId(int studentId, int sessionId) {
        return queryLong("SELECT id FROM results WHERE student_id = ? AND examination_session_id = ?", studentId, sessionId);
    }

    @Test
    void studentSeesNothingBeforePublication() throws Exception {
        // sem 1 marks are APPROVED but not yet published
        getAs(login("cs2024001"), "/api/student/results").andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
        getAs(login("cs2024001"), "/api/student/dashboard").andExpect(jsonPath("$.data.publishedResults").value(0))
                .andExpect(jsonPath("$.data.cgpa").doesNotExist());
    }

    @Test
    void studentListContainsOnlyOwnResults() throws Exception {
        publishHistoricResults(login("admin"));
        getAs(login("cs2024001"), "/api/student/results").andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2));
        // ECE students have no published results
        getAs(login("ec2024001"), "/api/student/results").andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    void studentCannotReadAnotherStudentsResultOrMarksheet() throws Exception {
        publishHistoricResults(login("admin"));
        Long othersResult = resultId(1, 1);
        Session intruder = login("cs2024002");
        getAs(intruder, "/api/student/results/" + othersResult).andExpect(status().isNotFound());
        getAs(intruder, "/api/student/results/" + othersResult + "/marksheet").andExpect(status().isNotFound());
        getAs(intruder, "/api/student/results/999999").andExpect(status().isNotFound());
        // own result works
        Long own = resultId(2, 1);
        getAs(intruder, "/api/student/results/" + own).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.rollNumber").value("CS2024002"))
                .andExpect(jsonPath("$.data.subjects.length()").value(3));
    }

    @Test
    void clientSuppliedStudentIdsAreIgnored() throws Exception {
        publishHistoricResults(login("admin"));
        Session s = login("cs2024002");
        getAs(s, "/api/student/results?studentId=1").andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].resultId").value(resultId(2, 2).intValue()));
        getAs(s, "/api/student/profile?studentId=1").andExpect(jsonPath("$.data.rollNumber").value("CS2024002"));
    }

    @Test
    void marksheetIsAPdfForTheOwnerOnly() throws Exception {
        publishHistoricResults(login("admin"));
        byte[] pdf = getAs(login("cs2024001"), "/api/student/results/" + resultId(1, 1) + "/marksheet")
                .andExpect(status().isOk()).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .content().contentType(MediaType.APPLICATION_PDF))
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(new String(pdf, 0, 5)).isEqualTo("%PDF-");
        assertThat(pdf.length).isGreaterThan(1000);
    }

    @Test
    void unpublishedApprovedMarksNeverLeakThroughStudentApis() throws Exception {
        // approve semester 3 CS301 but do not publish
        jdbc.update("UPDATE marks SET status = 'APPROVED' WHERE status = 'SUBMITTED'");
        publishHistoricResults(login("admin"));
        getAs(login("cs2024001"), "/api/student/results").andExpect(jsonPath("$.data.length()").value(2));
        String body = getAs(login("cs2024001"), "/api/student/results").andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain("CS301");
    }

    @Test
    void studentProfileShowsOwnDataOnly() throws Exception {
        getAs(login("ec2024001"), "/api/student/profile").andExpect(status().isOk())
                .andExpect(jsonPath("$.data.rollNumber").value("EC2024001"))
                .andExpect(jsonPath("$.data.departmentCode").value("ECE"))
                .andExpect(jsonPath("$.data.passwordHash").doesNotExist());
    }
}
