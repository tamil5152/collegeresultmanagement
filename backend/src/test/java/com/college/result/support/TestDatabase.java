package com.college.result.support;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Assumptions;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

/**
 * Real MySQL-compatible database for integration tests, configured by TEST_DB_URL / TEST_DB_USERNAME /
 * TEST_DB_PASSWORD. The database name MUST end in "_test" because it is wiped before every test.
 * When no database is reachable the integration tests are skipped, unless REQUIRE_DB_TESTS=true (used in CI),
 * in which case they fail instead.
 */
public final class TestDatabase {

    private static final String URL = env("TEST_DB_URL",
            "jdbc:mysql://localhost:3306/crms_test?useSSL=false&allowPublicKeyRetrieval=true");
    private static final String USER = env("TEST_DB_USERNAME", "crms");
    private static final String PASSWORD = env("TEST_DB_PASSWORD", "crms_dev_pw");
    private static Boolean available;

    private TestDatabase() {}

    public static String url() { return URL; }

    public static String username() { return USER; }

    public static String password() { return PASSWORD; }

    private static String env(String name, String fallback) {
        String v = System.getenv(name);
        return v == null || v.isBlank() ? fallback : v;
    }

    public static synchronized void assumeAvailable() {
        if (available == null) {
            try (Connection c = DriverManager.getConnection(URL, USER, PASSWORD)) {
                available = c.isValid(2);
            } catch (SQLException e) {
                available = false;
                System.err.println("Test database not reachable: " + e.getMessage());
            }
        }
        if (!available && "true".equalsIgnoreCase(System.getenv("REQUIRE_DB_TESTS"))) {
            throw new IllegalStateException("REQUIRE_DB_TESTS=true but the test database is unreachable: " + URL);
        }
        Assumptions.assumeTrue(available, "No test database reachable at " + URL
                + " - set TEST_DB_URL / TEST_DB_USERNAME / TEST_DB_PASSWORD to run integration tests");
    }

    /** Drops every table, then loads database/schema.sql and database/seed.sql. */
    public static void reset() {
        String db = URL.substring(URL.lastIndexOf('/') + 1).split("\\?")[0];
        if (!db.endsWith("_test")) {
            throw new IllegalStateException("Refusing to wipe database '" + db + "': name must end with _test");
        }
        try (Connection c = DriverManager.getConnection(URL, USER, PASSWORD); Statement st = c.createStatement()) {
            st.execute("SET FOREIGN_KEY_CHECKS = 0");
            List<String> tables = new ArrayList<>();
            try (ResultSet rs = st.executeQuery(
                    "SELECT table_name FROM information_schema.tables WHERE table_schema = DATABASE()")) {
                while (rs.next()) {
                    tables.add(rs.getString(1));
                }
            }
            for (String t : tables) {
                st.execute("DROP TABLE `" + t + "`");
            }
            st.execute("SET FOREIGN_KEY_CHECKS = 1");
            ScriptUtils.executeSqlScript(c, new FileSystemResource(Path.of("..", "database", "schema.sql")));
            ScriptUtils.executeSqlScript(c, new FileSystemResource(Path.of("..", "database", "seed.sql")));
        } catch (SQLException e) {
            throw new IllegalStateException("Could not reset the test database", e);
        }
    }
}
