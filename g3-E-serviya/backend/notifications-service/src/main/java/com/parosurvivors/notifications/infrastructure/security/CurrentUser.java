package com.parosurvivors.notifications.infrastructure.security;

import com.parosurvivors.notifications.application.exceptions.UnauthorizedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Id del usuario autenticado. Desde la Fase 4 notif es resource server: el principal es un {@link Jwt} y el
 * userId es su {@code sub}. Se conserva el soporte de un principal {@code Long} directo para los tests.
 * Los controladores la usan para no confiar en ids del path/body.
 */
public final class CurrentUser {

    private CurrentUser() {
    }

    public static Long id() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new UnauthorizedException("Authentication required");
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof Jwt jwt) {
            try {
                return Long.valueOf(jwt.getSubject());
            } catch (NumberFormatException ex) {
                throw new UnauthorizedException("Invalid token subject");
            }
        }
        if (principal instanceof Long userId) {
            return userId; // compat: tests con principal directo
        }
        throw new UnauthorizedException("Authentication required");
    }
}
