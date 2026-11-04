package com.college.result.support;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.college.result.security.LoginAttemptService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Full application context against a real database that is reset from schema.sql + seed.sql before each test.
 * Seeded ids (see database/seed.sql): students 1-6 are CSE, 7-10 ECE; assignments 7 (CS301, fac.cse1),
 * 8 (CS302, fac.cse2), 9 (CS303, fac.cse1), 10 (EC301, fac.ece1); sessions 1,2 are closed, 3 is open.
 */
@SpringBootTest
@AutoConfigureMockMvc
public abstract class AbstractIntegrationTest {

    public static final String PASSWORD = "Password@123";

    public record Session(Cookie cookie, String csrf) {}

    @Autowired protected MockMvc mvc;
    @Autowired protected JdbcTemplate jdbc;
    @Autowired protected ObjectMapper json;
    @Autowired protected LoginAttemptService attempts;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", TestDatabase::url);
        r.add("spring.datasource.username", TestDatabase::username);
        r.add("spring.datasource.password", TestDatabase::password);
        r.add("app.jwt.secret", () -> "integration-test-secret-integration-test-secret-0123");
        r.add("app.cookie.secure", () -> "false");
        r.add("app.cors.allowed-origins", () -> "http://localhost:5173");
    }

    @BeforeEach
    void resetDatabase() {
        TestDatabase.assumeAvailable();
        TestDatabase.reset();
        for (String u : new String[] {"admin", "fac.cse1", "fac.cse2", "fac.ece1", "cs2024001", "cs2024002",
                "cs2024006", "ec2024001"}) {
            attempts.recordSuccess(u);
        }
    }

    protected Session login(String username) throws Exception {
        MvcResult res = mvc.perform(request(HttpMethod.POST, "/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(java.util.Map.of("username", username, "password", PASSWORD))))
                .andExpect(status().isOk()).andReturn();
        String setCookie = res.getResponse().getHeader("Set-Cookie");
        String token = setCookie.substring(setCookie.indexOf('=') + 1, setCookie.indexOf(';'));
        String csrf = JsonPath.read(res.getResponse().getContentAsString(), "$.data.csrfToken");
        return new Session(new Cookie("CRMS_TOKEN", token), csrf);
    }

    protected ResultActions getAs(Session s, String url) throws Exception {
        return mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(url).cookie(s.cookie()));
    }

    protected ResultActions sendAs(Session s, HttpMethod method, String url, Object body) throws Exception {
        MockHttpServletRequestBuilder b = request(method, url).cookie(s.cookie())
                .header("X-CSRF-Token", s.csrf());
        if (body != null) {
            b.contentType(MediaType.APPLICATION_JSON).content(body instanceof String str ? str
                    : json.writeValueAsString(body));
        }
        return mvc.perform(b);
    }

    protected ResultActions postAs(Session s, String url, Object body) throws Exception {
        return sendAs(s, HttpMethod.POST, url, body);
    }

    protected ResultActions putAs(Session s, String url, Object body) throws Exception {
        return sendAs(s, HttpMethod.PUT, url, body);
    }

    protected Long queryLong(String sql, Object... args) {
        return jdbc.queryForObject(sql, Long.class, args);
    }

    /** Approves everything pending for sessions 1 and 2 is already done in the seed; this publishes both for CSE. */
    protected void publishHistoricResults(Session admin) throws Exception {
        postAs(admin, "/api/admin/results/publish", "{\"sessionId\":1,\"departmentId\":1}").andExpect(status().isOk());
        postAs(admin, "/api/admin/results/publish", "{\"sessionId\":2,\"departmentId\":1}").andExpect(status().isOk());
    }
}
