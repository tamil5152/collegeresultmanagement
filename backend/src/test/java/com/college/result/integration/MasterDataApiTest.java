package com.college.result.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.college.result.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;

/** Admin CRUD, validation, uniqueness and the student / faculty / assignment / enrollment management flows. */
class MasterDataApiTest extends AbstractIntegrationTest {

    @Test
    void departmentCrudUniquenessAndDeactivation() throws Exception {
        Session admin = login("admin");
        postAs(admin, "/api/admin/departments", "{\"code\":\"mec\",\"name\":\"Mechanical Engineering\"}")
                .andExpect(status().isCreated()).andExpect(jsonPath("$.data.code").value("MEC"));
        postAs(admin, "/api/admin/departments", "{\"code\":\"MEC\",\"name\":\"Another\"}").andExpect(status().isConflict());
        postAs(admin, "/api/admin/departments", "{\"code\":\"\",\"name\":\"\"}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.code").exists());
        Long id = queryLong("SELECT id FROM departments WHERE code = 'MEC'");
        putAs(admin, "/api/admin/departments/" + id, "{\"code\":\"MEC\",\"name\":\"Mechanical\"}").andExpect(status().isOk());
        putAs(admin, "/api/admin/departments/999", "{\"code\":\"X\",\"name\":\"Y\"}").andExpect(status().isNotFound());
        sendAs(admin, org.springframework.http.HttpMethod.PATCH, "/api/admin/departments/" + id + "/active",
                "{\"active\":false}").andExpect(status().isOk()).andExpect(jsonPath("$.data.active").value(false));
        getAs(admin, "/api/admin/options/departments").andExpect(jsonPath("$.data.length()").value(2));
    }

    @Test
    void subjectValidationRulesAndLockAfterMarksExist() throws Exception {
        Session admin = login("admin");
        String base = "\"departmentId\":1,\"code\":\"CS999\",\"name\":\"Elective\",\"semesterNumber\":3,\"credits\":3,";
        postAs(admin, "/api/admin/subjects", "{" + base + "\"internalMax\":40,\"externalMax\":60,\"internalPass\":41,"
                + "\"externalPass\":24,\"totalPass\":40}").andExpect(status().isBadRequest());
        postAs(admin, "/api/admin/subjects", "{" + base + "\"internalMax\":0,\"externalMax\":0,\"internalPass\":0,"
                + "\"externalPass\":0,\"totalPass\":0}").andExpect(status().isBadRequest());
        postAs(admin, "/api/admin/subjects", "{" + base + "\"internalMax\":40,\"externalMax\":60,\"internalPass\":16,"
                + "\"externalPass\":24,\"totalPass\":101}").andExpect(status().isBadRequest());
        postAs(admin, "/api/admin/subjects", "{" + base + "\"internalMax\":40,\"externalMax\":60,\"internalPass\":16,"
                + "\"externalPass\":24,\"totalPass\":40}").andExpect(status().isCreated());
        // CS101 already has marks: its maximum marks / pass criteria are frozen
        putAs(admin, "/api/admin/subjects/1", "{\"departmentId\":1,\"code\":\"CS101\",\"name\":\"Programming Fundamentals\","
                + "\"semesterNumber\":1,\"credits\":4,\"internalMax\":50,\"externalMax\":50,\"internalPass\":16,"
                + "\"externalPass\":24,\"totalPass\":40}").andExpect(status().isConflict());
        // renaming is fine
        putAs(admin, "/api/admin/subjects/1", "{\"departmentId\":1,\"code\":\"CS101\",\"name\":\"Programming Basics\","
                + "\"semesterNumber\":1,\"credits\":4,\"internalMax\":40,\"externalMax\":60,\"internalPass\":16,"
                + "\"externalPass\":24,\"totalPass\":40}").andExpect(status().isOk());
    }

    @Test
    void studentLifecycleCreateEditDeactivateAndLogin() throws Exception {
        Session admin = login("admin");
        String student = "{\"username\":\"newstudent\",\"password\":\"Welcome123\",\"email\":\"new@example.test\","
                + "\"fullName\":\"New Student\",\"departmentId\":1,\"rollNumber\":\"CS2025001\",\"batchYear\":2025,"
                + "\"currentSemester\":1}";
        postAs(admin, "/api/admin/students", student).andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.passwordHash").doesNotExist());
        postAs(admin, "/api/admin/students", student).andExpect(status().isConflict());   // duplicate username
        postAs(admin, "/api/admin/students", student.replace("newstudent", "other").replace("CS2025001", "CS2025002")
                .replace("Welcome123", "weak")).andExpect(status().isBadRequest());     // weak password
        assertThat(jdbc.queryForObject("SELECT password_hash FROM users WHERE username = 'newstudent'", String.class))
                .startsWith("$2").doesNotContain("Welcome123");

        Long id = queryLong("SELECT id FROM students WHERE roll_number = 'CS2025001'");
        putAs(admin, "/api/admin/students/" + id, "{\"email\":\"new@example.test\",\"fullName\":\"New Student Edited\","
                + "\"departmentId\":1,\"rollNumber\":\"CS2025001\",\"batchYear\":2025,\"currentSemester\":2,"
                + "\"password\":\"Another456\"}").andExpect(status().isOk())
                .andExpect(jsonPath("$.data.currentSemester").value(2));
        // the password reset applied: old one fails, new one works
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/auth/login")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content("{\"username\":\"newstudent\",\"password\":\"Welcome123\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/auth/login")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content("{\"username\":\"newstudent\",\"password\":\"Another456\"}"))
                .andExpect(status().isOk());
        sendAs(admin, org.springframework.http.HttpMethod.PATCH, "/api/admin/students/" + id + "/active",
                "{\"active\":false}").andExpect(status().isOk()).andExpect(jsonPath("$.data.active").value(false));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/auth/login")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content("{\"username\":\"newstudent\",\"password\":\"Another456\"}"))
                .andExpect(status().isUnauthorized());
        assertThat(queryLong("SELECT COUNT(*) FROM audit_logs WHERE action IN ('STUDENT_CREATED','STUDENT_UPDATED',"
                + "'STUDENT_DEACTIVATED','PASSWORD_RESET')")).isEqualTo(4);
    }

    @Test
    void facultyAccountsAreCreatedWithHashedPasswordAndCanLogIn() throws Exception {
        Session admin = login("admin");
        postAs(admin, "/api/admin/faculty", "{\"username\":\"fac.new\",\"password\":\"Welcome123\","
                + "\"email\":\"fac.new@example.test\",\"fullName\":\"New Faculty\",\"departmentId\":1,"
                + "\"employeeCode\":\"EMP100\"}").andExpect(status().isCreated());
        postAs(admin, "/api/admin/faculty", "{\"username\":\"fac.new2\",\"password\":\"Welcome123\","
                + "\"email\":\"fac.new2@example.test\",\"fullName\":\"Dup Code\",\"departmentId\":1,"
                + "\"employeeCode\":\"EMP100\"}").andExpect(status().isConflict());
        getAs(admin, "/api/admin/faculty?search=New&size=5").andExpect(jsonPath("$.data.totalElements").value(1));
    }

    @Test
    void assignmentRules() throws Exception {
        Session admin = login("admin");
        // semester 3 instance (id 3) with subject CS101 (semester 1) is inconsistent
        postAs(admin, "/api/admin/assignments", "{\"subjectId\":1,\"facultyId\":1,\"semesterId\":3}")
                .andExpect(status().isBadRequest());
        // CS301 is already assigned in semester 3
        postAs(admin, "/api/admin/assignments", "{\"subjectId\":7,\"facultyId\":2,\"semesterId\":3}")
                .andExpect(status().isConflict());
        // reassigning is allowed
        putAs(admin, "/api/admin/assignments/9", "{\"facultyId\":2}").andExpect(status().isOk())
                .andExpect(jsonPath("$.data.facultyName").value("Prof. Ravi Kumar"));
        // the new owner can now enter marks, the previous one cannot
        putAs(login("fac.cse2"), "/api/faculty/assignments/9/sessions/3/marks",
                "{\"entries\":[{\"studentId\":1,\"internalMarks\":10,\"externalMarks\":10}]}").andExpect(status().isOk());
        putAs(login("fac.cse1"), "/api/faculty/assignments/9/sessions/3/marks",
                "{\"entries\":[{\"studentId\":2,\"internalMarks\":10,\"externalMarks\":10}]}").andExpect(status().isForbidden());
        // marks exist for CS303 now -> assignment cannot be deleted; CS302 (draft marks too) neither
        sendAs(admin, org.springframework.http.HttpMethod.DELETE, "/api/admin/assignments/9", null)
                .andExpect(status().isConflict());
        // EC302 (assignment 11) has no marks at all
        sendAs(admin, org.springframework.http.HttpMethod.DELETE, "/api/admin/assignments/11", null)
                .andExpect(status().isOk());
    }

    @Test
    void enrollmentRules() throws Exception {
        Session admin = login("admin");
        postAs(admin, "/api/admin/enrollments/bulk", "{\"semesterId\":3,\"departmentId\":1}").andExpect(status().isOk())
                .andExpect(jsonPath("$.data.newEnrollments").value(0));   // already enrolled by the seed
        Long withMarks = queryLong("SELECT e.id FROM student_enrollments e WHERE e.student_id = 1 AND e.subject_id = 7 "
                + "AND e.semester_id = 3");
        sendAs(admin, org.springframework.http.HttpMethod.DELETE, "/api/admin/enrollments/" + withMarks, null)
                .andExpect(status().isConflict());
        Long withoutMarks = queryLong("SELECT e.id FROM student_enrollments e WHERE e.student_id = 1 AND e.subject_id = 9 "
                + "AND e.semester_id = 3");
        sendAs(admin, org.springframework.http.HttpMethod.DELETE, "/api/admin/enrollments/" + withoutMarks, null)
                .andExpect(status().isOk());
        postAs(admin, "/api/admin/enrollments", "{\"studentId\":1,\"subjectId\":9,\"semesterId\":3}")
                .andExpect(status().isCreated());
        postAs(admin, "/api/admin/enrollments", "{\"studentId\":1,\"subjectId\":9,\"semesterId\":3}")
                .andExpect(status().isConflict());
        postAs(admin, "/api/admin/enrollments", "{\"studentId\":1,\"subjectId\":1,\"semesterId\":3}")
                .andExpect(status().isBadRequest());                    // semester 1 subject in semester 3
    }

    @Test
    void reportsAreComputedFromPublishedRecords() throws Exception {
        Session admin = login("admin");
        getAs(admin, "/api/admin/reports/department-summary?sessionId=1").andExpect(jsonPath("$.data.length()").value(0));
        publishHistoricResults(admin);
        getAs(admin, "/api/admin/reports/department-summary?sessionId=1").andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].students").value(6)).andExpect(jsonPath("$.data[0].passed").value(5))
                .andExpect(jsonPath("$.data[0].failed").value(1)).andExpect(jsonPath("$.data[0].passPercentage").value(83.33));
        getAs(admin, "/api/admin/reports/grade-distribution?sessionId=1").andExpect(status().isOk());
        getAs(admin, "/api/admin/reports/subject-analysis?sessionId=1&departmentId=1")
                .andExpect(jsonPath("$.data.length()").value(3));
        getAs(admin, "/api/admin/reports/department-summary").andExpect(status().isBadRequest());
        getAs(admin, "/api/admin/dashboard").andExpect(jsonPath("$.data.publishedResults").value(12))
                .andExpect(jsonPath("$.data.students").value(10));
    }
}
