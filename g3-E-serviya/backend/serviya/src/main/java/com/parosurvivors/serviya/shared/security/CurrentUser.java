package com.parosurvivors.serviya.shared.security;

import com.parosurvivors.serviya.shared.exceptions.UnauthorizedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Utilidad para obtener el id del usuario autenticado desde el contexto de seguridad.
 * El principal lo coloca {@code JwtAuthenticationFilter} (es el userId del JWT). Los
 * controladores {@code /me} la usan para no confiar en ids del body (RNF / seccion C.1
 * de estructura-endpoints.md).
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
