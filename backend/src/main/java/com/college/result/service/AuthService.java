package com.college.result.service;

import com.college.result.common.ApiException;
import com.college.result.dto.AuthDtos.LoginRequest;
import com.college.result.dto.AuthDtos.SessionInfo;
import com.college.result.dto.AuthDtos.SessionUser;
import com.college.result.model.User;
import com.college.result.repository.UserRepository;
import com.college.result.security.AuthUser;
import com.college.result.security.JwtService;
import com.college.result.security.LoginAttemptService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final JwtService jwt;
    private final LoginAttemptService attempts;
    private final AuditService audit;
    /** Compared against when the username does not exist, so response time does not reveal valid usernames. */
    private final String dummyHash;

    public AuthService(UserRepository users, PasswordEncoder encoder, JwtService jwt, LoginAttemptService attempts,
                       AuditService audit) {
        this.users = users;
        this.encoder = encoder;
        this.jwt = jwt;
        this.attempts = attempts;
        this.audit = audit;
        this.dummyHash = encoder.encode("not-a-real-password");
    }

    public record LoginResult(String token, SessionInfo info) {}

    @Transactional
    public LoginResult login(LoginRequest request) {
        String username = request.username().trim();
        if (attempts.isLocked(username)) {
            throw ApiException.tooManyRequests("Too many failed attempts. Try again later.");
        }
        User user = users.findByUsername(username).orElse(null);
        boolean ok = encoder.matches(request.password(), user == null ? dummyHash : user.passwordHash());
        if (user == null || !ok || !user.active()) {
            attempts.recordFailure(username);
            throw ApiException.unauthorized("Invalid username or password");
        }
        attempts.recordSuccess(username);
        users.touchLastLogin(user.id());
        String csrf = jwt.newCsrfToken();
        String token = jwt.issue(user, csrf);
        audit.log(new AuthUser(user.id(), user.username(), user.fullName(), user.role(), csrf),
                "LOGIN", "USER", user.id(), null);
        return new LoginResult(token, info(user, csrf));
    }

    public SessionInfo current(AuthUser principal) {
        return new SessionInfo(new SessionUser(principal.userId(), principal.username(), principal.fullName(),
                principal.role().name()), principal.csrfToken());
    }

    @Transactional
    public void changePassword(AuthUser principal, String currentPassword, String newPassword) {
        User user = users.findById(principal.userId()).orElseThrow(() -> ApiException.unauthorized("Unknown user"));
        if (!encoder.matches(currentPassword, user.passwordHash())) {
            throw ApiException.badRequest("Current password is incorrect");
        }
        if (encoder.matches(newPassword, user.passwordHash())) {
            throw ApiException.badRequest("New password must be different from the current password");
        }
        users.updatePassword(user.id(), encoder.encode(newPassword));
        audit.log(principal, "PASSWORD_CHANGED", "USER", user.id(), null);
    }

    private SessionInfo info(User u, String csrf) {
        return new SessionInfo(new SessionUser(u.id(), u.username(), u.fullName(), u.role().name()), csrf);
    }
}
