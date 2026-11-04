package com.college.result.controller;

import com.college.result.common.ApiResponse;
import com.college.result.dto.AuthDtos.ChangePasswordRequest;
import com.college.result.dto.AuthDtos.LoginRequest;
import com.college.result.dto.AuthDtos.SessionInfo;
import com.college.result.security.AuthUser;
import com.college.result.security.CookieFactory;
import com.college.result.security.JwtService;
import com.college.result.service.AuthService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Authentication")
public class AuthController {

    private final AuthService service;
    private final CookieFactory cookies;
    private final JwtService jwt;

    public AuthController(AuthService service, CookieFactory cookies, JwtService jwt) {
        this.service = service;
        this.cookies = cookies;
        this.jwt = jwt;
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<SessionInfo>> login(@Valid @RequestBody LoginRequest request) {
        AuthService.LoginResult result = service.login(request);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookies.create(result.token(), jwt.lifetime()).toString())
                .body(ApiResponse.ok(result.info()));
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout() {
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookies.clear().toString())
                .body(ApiResponse.ok("Logged out", null));
    }

    @GetMapping("/me")
    public ApiResponse<SessionInfo> me(@AuthenticationPrincipal AuthUser user) {
        return ApiResponse.ok(service.current(user));
    }

    @PostMapping("/change-password")
    public ApiResponse<Void> changePassword(@AuthenticationPrincipal AuthUser user,
                                            @Valid @RequestBody ChangePasswordRequest request) {
        service.changePassword(user, request.currentPassword(), request.newPassword());
        return ApiResponse.ok("Password changed", null);
    }
}
