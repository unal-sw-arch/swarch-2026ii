package com.parosurvivors.notifications.infrastructure.security;

import com.parosurvivors.notifications.application.exceptions.UnauthorizedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Id del usuario autenticado (principal que coloca {@link JwtAuthenticationFilter}: el userId del JWT).
 * Los controladores la usan para no confiar en ids del path/body.
 */
public final class CurrentUser {

    private CurrentUser() {
    }

    public static Long id() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof Long userId)) {
            throw new UnauthorizedException("Authentication required");
        }
        return userId;
    }
}
