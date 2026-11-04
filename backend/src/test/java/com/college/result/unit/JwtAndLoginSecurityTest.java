package com.college.result.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.college.result.config.AppProperties;
import com.college.result.model.Role;
import com.college.result.model.User;
import com.college.result.security.JwtService;
import com.college.result.security.LoginAttemptService;
import java.util.List;
import org.junit.jupiter.api.Test;

class JwtAndLoginSecurityTest {

    private static final String SECRET = "unit-test-secret-unit-test-secret-0123456789";
    private static final User USER = new User(42, "fac1", "f@example.test", "hash", "Faculty One", Role.FACULTY, true);

    private static JwtService service(String secret, long minutes) {
        return new JwtService(new AppProperties("College", new AppProperties.Jwt(secret, minutes),
                new AppProperties.Cookie("CRMS_TOKEN", true, "Lax"), new AppProperties.Cors(List.of()), null));
    }

    @Test
    void issuedTokenVerifiesAndCarriesRoleAndCsrfClaim() {
        JwtService jwt = service(SECRET, 5);
        String csrf = jwt.newCsrfToken();
        var verified = jwt.verify(jwt.issue(USER, csrf)).orElseThrow();
        assertThat(verified.userId()).isEqualTo(42);
        assertThat(verified.role()).isEqualTo(Role.FACULTY);
        assertThat(verified.csrfToken()).isEqualTo(csrf);
    }

    @Test
    void tamperedTokenIsRejected() {
        JwtService jwt = service(SECRET, 5);
        String token = jwt.issue(USER, "csrf");
        String[] parts = token.split("\\.");
        String forgedPayload = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(
                "{\"sub\":\"1\",\"role\":\"ADMIN\",\"csrf\":\"csrf\"}".getBytes());
        assertThat(jwt.verify(parts[0] + "." + forgedPayload + "." + parts[2])).isEmpty();
        assertThat(jwt.verify(token + "x")).isEmpty();
    }

    @Test
    void tokenSignedWithAnotherSecretIsRejected() {
        String foreign = service("another-secret-another-secret-another-secret-9", 5).issue(USER, "c");
        assertThat(service(SECRET, 5).verify(foreign)).isEmpty();
    }

    @Test
    void expiredTokenIsRejected() {
        JwtService jwt = service(SECRET, -1);
        assertThat(jwt.verify(jwt.issue(USER, "c"))).isEmpty();
    }

    @Test
    void garbageAndNullAreRejected() {
        JwtService jwt = service(SECRET, 5);
        assertThat(jwt.verify(null)).isEmpty();
        assertThat(jwt.verify("")).isEmpty();
        assertThat(jwt.verify("not.a.jwt")).isEmpty();
    }

    @Test
    void shortSecretFailsFast() {
        assertThatThrownBy(() -> service("too-short", 5)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> service(null, 5)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void accountIsLockedAfterFiveFailuresAndClearedBySuccess() {
        LoginAttemptService attempts = new LoginAttemptService();
        for (int i = 0; i < 4; i++) {
            attempts.recordFailure("Alice");
        }
        assertThat(attempts.isLocked("alice")).isFalse();
        attempts.recordFailure("ALICE");
        assertThat(attempts.isLocked("alice")).isTrue();
        attempts.recordSuccess("alice");
        assertThat(attempts.isLocked("alice")).isFalse();
    }
}
