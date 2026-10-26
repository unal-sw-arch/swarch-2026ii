package com.parosurvivors.notifications.infrastructure.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Lee {@code Authorization: Bearer <jwt>} y, si el token es válido, deja el Authentication en el contexto.
 * Sin token (o inválido) la petición sigue sin autenticar y la regla de autorización decide (→ 401).
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtTokenVerifier tokenVerifier;

    public JwtAuthenticationFilter(JwtTokenVerifier tokenVerifier) {
        this.tokenVerifier = tokenVerifier;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith(BEARER_PREFIX)) {
            tokenVerifier.resolve(header.substring(BEARER_PREFIX.length()))
                    .ifPresent(auth -> SecurityContextHolder.getContext().setAuthentication(auth));
        }
        chain.doFilter(request, response);
    }
}
