package com.college.result.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.college.result.support.AbstractIntegrationTest;
import jakarta.servlet.http.Cookie;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

class AuthApiTest extends AbstractIntegrationTest {

    private String body(String user, String pw) throws Exception {
        return json.writeValueAsString(Map.of("username", user, "password", pw));
    }

    @Test
    void loginSetsHttpOnlyCookieAndReturnsCsrfTokenButNoJwtInBody() throws Exception {
        MvcResult res = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(body("admin", PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.user.role").value("ADMIN"))
                .andExpect(jsonPath("$.data.csrfToken").isNotEmpty())
                .andExpect(header().string("Set-Cookie", containsString("HttpOnly")))
                .andExpect(header().string("Set-Cookie", containsString("SameSite=Lax"))).andReturn();
        assertThat(res.getResponse().getContentAsString()).doesNotContain("eyJ"); // no JWT in the payload
        assertThat(res.getResponse().getContentAsString()).doesNotContain("password");
    }

    @Test
    void wrongPasswordAndUnknownUserGetTheSameGenericError() throws Exception {
        String a = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(body("admin", "wrong-pass1"))).andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();
        String b = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(body("nobody", "wrong-pass1"))).andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();
        assertThat(a).contains("Invalid username or password");
        assertThat(b).contains("Invalid username or password");
    }

    @Test
    void loginValidatesInput() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.username").exists());
    }

    @Test
    void inactiveUserCannotLogIn() throws Exception {
        jdbc.update("UPDATE users SET active = 0 WHERE username = 'cs2024001'");
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(body("cs2024001", PASSWORD))).andExpect(status().isUnauthorized());
    }

    @Test
    void deactivatingAUserInvalidatesTheirExistingSessionImmediately() throws Exception {
        Session s = login("cs2024001");
        getAs(s, "/api/student/profile").andExpect(status().isOk());
        jdbc.update("UPDATE users SET active = 0 WHERE username = 'cs2024001'");
        getAs(s, "/api/student/profile").andExpect(status().isUnauthorized());
    }

    @Test
    void meRequiresAuthenticationAndReturnsTheCurrentUser() throws Exception {
        mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
        Session s = login("fac.cse1");
        getAs(s, "/api/auth/me").andExpect(status().isOk()).andExpect(jsonPath("$.data.user.username").value("fac.cse1"))
                .andExpect(jsonPath("$.data.csrfToken").value(s.csrf()));
    }

    @Test
    void tamperedExpiredOrGarbageCookiesAreRejected() throws Exception {
        Session s = login("admin");
        String t = s.cookie().getValue();
        mvc.perform(get("/api/admin/dashboard").cookie(new Cookie("CRMS_TOKEN", t + "x")))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/admin/dashboard").cookie(new Cookie("CRMS_TOKEN", "garbage")))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/admin/dashboard")).andExpect(status().isUnauthorized());
    }

    @Test
    void stateChangingRequestsNeedTheCsrfHeader() throws Exception {
        Session s = login("admin");
        String dept = "{\"code\":\"MEC\",\"name\":\"Mechanical\"}";
        mvc.perform(post("/api/admin/departments").cookie(s.cookie()).contentType(MediaType.APPLICATION_JSON)
                .content(dept)).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("CSRF_INVALID"));
        mvc.perform(post("/api/admin/departments").cookie(s.cookie()).header("X-CSRF-Token", "wrong")
                .contentType(MediaType.APPLICATION_JSON).content(dept)).andExpect(status().isForbidden());
        postAs(s, "/api/admin/departments", dept).andExpect(status().isCreated());
    }

    @Test
    void logoutClearsTheCookie() throws Exception {
        mvc.perform(post("/api/auth/logout")).andExpect(status().isOk())
                .andExpect(header().string("Set-Cookie", containsString("Max-Age=0")));
    }

    @Test
    void accountLocksAfterRepeatedFailures() throws Exception {
        for (int i = 0; i < 5; i++) {
            mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                    .content(body("cs2024006", "bad-password1"))).andExpect(status().isUnauthorized());
        }
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(body("cs2024006", PASSWORD))).andExpect(status().isTooManyRequests());
    }

    @Test
    void changePasswordRequiresCurrentPasswordAndStrongNewOne() throws Exception {
        Session s = login("cs2024002");
        postAs(s, "/api/auth/change-password", Map.of("currentPassword", "nope", "newPassword", "Better#Pass1"))
                .andExpect(status().isBadRequest());
        postAs(s, "/api/auth/change-password", Map.of("currentPassword", PASSWORD, "newPassword", "short"))
                .andExpect(status().isBadRequest());
        postAs(s, "/api/auth/change-password", Map.of("currentPassword", PASSWORD, "newPassword", "Better#Pass1"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(body("cs2024002", PASSWORD))).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(body("cs2024002", "Better#Pass1"))).andExpect(status().isOk());
    }

    @Test
    void passwordsAreStoredAsBcryptHashes() {
        String hash = jdbc.queryForObject("SELECT password_hash FROM users WHERE username = 'admin'", String.class);
        assertThat(hash).startsWith("$2").doesNotContain(PASSWORD);
    }
}
