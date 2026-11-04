package com.college.result.security;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

/**
 * Minimal in-memory brute-force protection: after MAX_FAILURES failed logins a username is locked for LOCK_TIME.
 * State is per application instance (documented limitation for multi-instance deployments).
 */
@Service
public class LoginAttemptService {

    static final int MAX_FAILURES = 5;
    static final Duration LOCK_TIME = Duration.ofMinutes(15);

    private record State(int failures, Instant lockedUntil) {}

    private final ConcurrentHashMap<String, State> states = new ConcurrentHashMap<>();

    public boolean isLocked(String username) {
        State s = states.get(key(username));
        return s != null && s.lockedUntil() != null && Instant.now().isBefore(s.lockedUntil());
    }

    public void recordFailure(String username) {
        states.compute(key(username), (k, old) -> {
            int failures = (old == null || isExpired(old)) ? 1 : old.failures() + 1;
            return failures >= MAX_FAILURES ? new State(0, Instant.now().plus(LOCK_TIME)) : new State(failures, null);
        });
    }

    public void recordSuccess(String username) {
        states.remove(key(username));
    }

    private boolean isExpired(State s) {
        return s.lockedUntil() != null && !Instant.now().isBefore(s.lockedUntil());
    }

    private String key(String username) {
        return username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
    }
}
