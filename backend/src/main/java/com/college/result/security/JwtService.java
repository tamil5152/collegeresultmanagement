package com.college.result.security;

import com.college.result.config.AppProperties;
import com.college.result.model.Role;
import com.college.result.model.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.Optional;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Service;

/** Issues and verifies HS256 JWTs. Signature and expiry are always verified. */
@Service
public class JwtService {

    private static final int MIN_SECRET_BYTES = 32;
    private final SecretKey key;
    private final Duration lifetime;
    private final SecureRandom random = new SecureRandom();

    public JwtService(AppProperties props) {
        String secret = props.jwt().secret();
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
            throw new IllegalStateException("JWT_SECRET must be set and at least " + MIN_SECRET_BYTES + " bytes long");
        }
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.lifetime = Duration.ofMinutes(props.jwt().expiryMinutes());
    }

    public Duration lifetime() {
        return lifetime;
    }

    public String newCsrfToken() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public String issue(User user, String csrfToken) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(Long.toString(user.id()))
                .claim("role", user.role().name())
                .claim("csrf", csrfToken)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(lifetime)))
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }

    public record Verified(long userId, Role role, String csrfToken) {}

    /** Returns the verified claims, or empty when the token is missing, tampered or expired. */
    public Optional<Verified> verify(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        try {
            Claims claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
            return Optional.of(new Verified(Long.parseLong(claims.getSubject()),
                    Role.valueOf(claims.get("role", String.class)), claims.get("csrf", String.class)));
        } catch (JwtException | IllegalArgumentException ex) {
            return Optional.empty();
        }
    }
}
