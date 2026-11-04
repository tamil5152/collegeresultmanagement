package com.college.result.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.college.result.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;

/** Server-side role enforcement - independent of anything the React app hides. */
class RoleAccessApiTest extends AbstractIntegrationTest {

    private static final String[] ADMIN_URLS = {"/api/admin/dashboard", "/api/admin/students", "/api/admin/faculty",
            "/api/admin/departments", "/api/admin/subjects", "/api/admin/marks", "/api/admin/audit-logs",
            "/api/admin/grading-rules", "/api/admin/reports/department-summary?sessionId=1"};
    private static final String[] FACULTY_URLS = {"/api/faculty/dashboard", "/api/faculty/assignments",
            "/api/faculty/submissions"};
    private static final String[] STUDENT_URLS = {"/api/student/dashboard", "/api/student/profile",
            "/api/student/results"};

    @Test
    void anonymousUsersGet401EverywhereExceptLoginAndHealth() throws Exception {
        for (String url : concat(ADMIN_URLS, FACULTY_URLS, STUDENT_URLS)) {
            mvc.perform(get(url)).andExpect(status().isUnauthorized());
        }
        mvc.perform(get("/api/health")).andExpect(status().isOk());
    }

    @Test
    void adminOnlySeesAdminEndpoints() throws Exception {
        Session s = login("admin");
        for (String url : ADMIN_URLS) {
            getAs(s, url).andExpect(status().isOk());
        }
        for (String url : concat(FACULTY_URLS, STUDENT_URLS)) {
            getAs(s, url).andExpect(status().isForbidden());
        }
    }

    @Test
    void facultyOnlySeesFacultyEndpoints() throws Exception {
        Session s = login("fac.cse1");
        for (String url : FACULTY_URLS) {
            getAs(s, url).andExpect(status().isOk());
        }
        for (String url : concat(ADMIN_URLS, STUDENT_URLS)) {
            getAs(s, url).andExpect(status().isForbidden());
        }
    }

    @Test
    void studentOnlySeesStudentEndpoints() throws Exception {
        Session s = login("cs2024001");
        for (String url : STUDENT_URLS) {
            getAs(s, url).andExpect(status().isOk());
        }
        for (String url : concat(ADMIN_URLS, FACULTY_URLS)) {
            getAs(s, url).andExpect(status().isForbidden());
        }
    }

    @Test
    void onlyAdminCanPublishResultsAndReviewMarks() throws Exception {
        String publish = "{\"sessionId\":1,\"departmentId\":1}";
        for (String user : new String[] {"fac.cse1", "cs2024001"}) {
            Session s = login(user);
            postAs(s, "/api/admin/results/publish", publish).andExpect(status().isForbidden());
            postAs(s, "/api/admin/marks/review", "{\"markIds\":[64],\"action\":\"APPROVE\"}")
                    .andExpect(status().isForbidden());
            postAs(s, "/api/admin/marks/1/correct", "{\"internalMarks\":1,\"externalMarks\":1,\"reason\":\"long enough reason\"}")
                    .andExpect(status().isForbidden());
        }
        Integer published = jdbc.queryForObject("SELECT COUNT(*) FROM results", Integer.class);
        org.assertj.core.api.Assertions.assertThat(published).isZero();
    }

    @Test
    void unknownPathsAreDeniedNotExposed() throws Exception {
        mvc.perform(get("/api/unknown")).andExpect(status().isUnauthorized());
        getAs(login("admin"), "/api/unknown").andExpect(status().isForbidden());
    }

    private static String[] concat(String[]... arrays) {
        return java.util.Arrays.stream(arrays).flatMap(java.util.Arrays::stream).toArray(String[]::new);
    }
}
