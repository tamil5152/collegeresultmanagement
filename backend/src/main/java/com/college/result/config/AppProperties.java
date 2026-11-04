package com.college.result.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "app")
public record AppProperties(
        @DefaultValue("College Result Management") String collegeName,
        Jwt jwt,
        Cookie cookie,
        Cors cors,
        BootstrapAdmin bootstrapAdmin) {

    public record Jwt(String secret, @DefaultValue("60") long expiryMinutes) {}

    public record Cookie(@DefaultValue("CRMS_TOKEN") String name,
                         @DefaultValue("true") boolean secure,
                         @DefaultValue("Lax") String sameSite) {}

    public record Cors(@DefaultValue({}) List<String> allowedOrigins) {}

    public record BootstrapAdmin(String username, String password, String email, String fullName) {}
}
