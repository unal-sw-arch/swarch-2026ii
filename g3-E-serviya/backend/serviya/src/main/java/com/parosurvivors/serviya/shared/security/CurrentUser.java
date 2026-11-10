package com.parosurvivors.serviya.shared.security;

import com.parosurvivors.serviya.shared.exceptions.UnauthorizedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Utilidad para obtener el id del usuario autenticado desde el contexto de seguridad.
 * Desde la Fase 4 el monolito es resource server: el principal es un {@link Jwt} y el userId es su
 * {@code sub}. Se conserva el soporte de un principal {@code Long} directo para los tests de slice web
 * ({@code addFilters=false}). Los controladores {@code /me} la usan para no confiar en ids del body.
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
            return userId; // compat: tests de slice web con principal directo
        }
        throw new UnauthorizedException("Authentication required");
    }

    /**
     * Indica si el usuario autenticado tiene el rol ADMIN. Los roles los coloca
     * {@code JwtService} como authorities {@code ROLE_<NAME>}. Sirve para autorizaciones
     * de grano fino "participante O admin" que no encajan en un {@code @PreAuthorize} simple.
     */
    public static boolean isAdmin() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()));
    }
}
