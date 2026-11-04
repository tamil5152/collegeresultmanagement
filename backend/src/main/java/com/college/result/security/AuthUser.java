package com.college.result.security;

import com.college.result.model.Role;
import java.security.Principal;

/** The authenticated identity placed in the SecurityContext. Identity always comes from the verified token. */
public record AuthUser(long userId, String username, String fullName, Role role, String csrfToken)
        implements Principal {

    @Override
    public String getName() {
        return username;
    }
}
