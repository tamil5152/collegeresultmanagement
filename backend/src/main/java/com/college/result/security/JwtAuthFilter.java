package com.college.result.security;

import com.college.result.model.User;
import com.college.result.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Authenticates a request from the HttpOnly JWT cookie. The user is re-read from the database on every request,
 * so deactivating an account or changing a role takes effect immediately. State-changing requests must also carry
 * the X-CSRF-Token header matching the token's csrf claim (CSRF protection for cookie authentication).
 */
@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    public static final String CSRF_HEADER = "X-CSRF-Token";
    private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS", "TRACE");

    private final JwtService jwtService;
    private final CookieFactory cookies;
    private final UserRepository users;
    private final JsonErrorWriter errors;

    public JwtAuthFilter(JwtService jwtService, CookieFactory cookies, UserRepository users, JsonErrorWriter errors) {
        this.jwtService = jwtService;
        this.cookies = cookies;
        this.users = users;
        this.errors = errors;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Optional<JwtService.Verified> verified = jwtService.verify(readCookie(request));
        if (verified.isPresent()) {
            Optional<User> user = users.findById(verified.get().userId());
            if (user.isPresent() && user.get().active() && user.get().role() == verified.get().role()) {
                if (!SAFE_METHODS.contains(request.getMethod()) && !csrfValid(request, verified.get())) {
                    errors.write(response, 403, "CSRF_INVALID", "Missing or invalid CSRF token");
                    return;
                }
                User u = user.get();
                AuthUser principal = new AuthUser(u.id(), u.username(), u.fullName(), u.role(),
                        verified.get().csrfToken());
                var auth = new UsernamePasswordAuthenticationToken(principal, null,
                        List.of(new SimpleGrantedAuthority("ROLE_" + u.role().name())));
                SecurityContextHolder.getContext().setAuthentication(auth);
            }
        }
        chain.doFilter(request, response);
    }

    private String readCookie(HttpServletRequest request) {
        Cookie[] all = request.getCookies();
        if (all == null) {
            return null;
        }
        for (Cookie c : all) {
            if (c.getName().equals(cookies.name())) {
                return c.getValue();
            }
        }
        return null;
    }

    private boolean csrfValid(HttpServletRequest request, JwtService.Verified verified) {
        String header = request.getHeader(CSRF_HEADER);
        if (header == null || verified.csrfToken() == null) {
            return false;
        }
        return MessageDigest.isEqual(header.getBytes(StandardCharsets.UTF_8),
                verified.csrfToken().getBytes(StandardCharsets.UTF_8));
    }
}
