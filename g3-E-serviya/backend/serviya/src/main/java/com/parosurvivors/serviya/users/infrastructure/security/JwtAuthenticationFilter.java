package com.parosurvivors.serviya.users.infrastructure.security;

import com.parosurvivors.serviya.users.application.ports.output.UserReadPort;
import com.parosurvivors.serviya.users.domain.RoleName;
import com.parosurvivors.serviya.users.domain.User;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Filtro que, si hay un header {@code Authorization: Bearer <jwt>} valido, puebla el
 * {@link SecurityContextHolder} con el userId (principal) y sus roles. Si no hay token o es
 * invalido, no autentica: las reglas de {@code SecurityConfig} decidiran si el endpoint exige login.
 *
 * <p>Los roles se revalidan en cada peticion consultando la base de datos para que los cambios
 * de rol (revocacion, asignacion) surtan efecto inmediatamente sin esperar a que expire el JWT.
 *
 * <p>No es un {@code @Component}: lo instancia {@code SecurityConfig} para evitar el auto-registro
 * de Spring Boot que lo aplicaria tambien fuera de la cadena de Spring Security.
 */
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final UserReadPort userReadPort;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        boolean shouldContinue = true;
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith(BEARER_PREFIX)
                && SecurityContextHolder.getContext().getAuthentication() == null) {
            String token = header.substring(BEARER_PREFIX.length());
            shouldContinue = jwtService.resolve(token)
                    .map(auth -> {
                        Authentication updatedAuth = revalidateRoles(auth);
                        if (updatedAuth != null) {
                            SecurityContextHolder.getContext().setAuthentication(updatedAuth);
                            return true;
                        }
                        SecurityContextHolder.clearContext();
                        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                        return false;
                    })
                    .orElse(true);
        }
        if (shouldContinue) {
            filterChain.doFilter(request, response);
        }
    }

    /**
     * Revalida que el usuario este activo y actualiza sus roles con los valores actuales de la BD.
     * Retorna el Authentication actualizado o null si el usuario no esta activo/no existe.
     */
    private Authentication revalidateRoles(Authentication authentication) {
        if (authentication == null || authentication.getPrincipal() == null) {
            return null;
        }
        Object principal = authentication.getPrincipal();
        Long userId = principal instanceof Long l ? l : null;
        if (userId == null && principal instanceof UsernamePasswordAuthenticationToken token) {
            userId = token.getPrincipal() instanceof Long l ? l : null;
        }
        if (userId == null) {
            return null;
        }
        return userReadPort.findById(userId)
                .filter(User::isActive)
                .map(user -> buildAuthenticationWithFreshRoles(user, authentication))
                .orElse(null);
    }

    private Authentication buildAuthenticationWithFreshRoles(User user, Authentication originalAuth) {
        List<SimpleGrantedAuthority> freshAuthorities = user.getRoles().stream()
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role.name()))
                .toList();
        return new UsernamePasswordAuthenticationToken(user.getId(), null, freshAuthorities);
    }
}
