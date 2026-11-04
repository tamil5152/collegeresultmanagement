package com.college.result.security;

import com.college.result.config.AppProperties;
import java.time.Duration;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/** Builds the HttpOnly authentication cookie. */
@Component
public class CookieFactory {

    private final AppProperties.Cookie cfg;

    public CookieFactory(AppProperties props) {
        this.cfg = props.cookie();
    }

    public String name() {
        return cfg.name();
    }

    public ResponseCookie create(String token, Duration maxAge) {
        return base(token).maxAge(maxAge).build();
    }

    public ResponseCookie clear() {
        return base("").maxAge(Duration.ZERO).build();
    }

    private ResponseCookie.ResponseCookieBuilder base(String value) {
        return ResponseCookie.from(cfg.name(), value)
                .httpOnly(true)
                .secure(cfg.secure())
                .sameSite(cfg.sameSite())
                .path("/");
    }
}
