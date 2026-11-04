package com.college.result.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info().title("College Result Management API").version("1.0.0")
                        .description("Log in with POST /api/auth/login (sets an HttpOnly cookie and returns a csrfToken). "
                                + "Send that value in the X-CSRF-Token header on every POST/PUT/PATCH/DELETE request "
                                + "(use the Authorize button)."))
                .components(new Components().addSecuritySchemes("csrfToken",
                        new SecurityScheme().type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.HEADER).name("X-CSRF-Token")))
                .addSecurityItem(new SecurityRequirement().addList("csrfToken"));
    }
}
