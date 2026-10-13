package com.parosurvivors.serviya.shared.ratelimit;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Sujeto de una cuota (D-09), resuelto de forma estándar para filtros e interceptores: el usuario
 * autenticado si lo hay ({@code user}:<id del JWT>), o la IP del cliente si es anónimo ({@code ip}:<addr>).
 *
 * <p>La IP sale de {@link HttpServletRequest#getRemoteAddr()}, que con {@code server.forward-headers-strategy}
 * ya resuelve la IP real del cliente solo cuando el salto inmediato es un proxy de confianza (no se parsea
 * {@code X-Forwarded-For} a mano, que seria falsificable).</p>
 */
public record RateLimitSubject(String type, String id) {

    public static final String TYPE_USER = "user";
    public static final String TYPE_IP = "ip";

    /** Resuelve el sujeto: {@code user} si hay un principal autenticado (userId Long), si no {@code ip}. */
    public static RateLimitSubject of(HttpServletRequest request) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && auth.getPrincipal() instanceof Long userId) {
            return new RateLimitSubject(TYPE_USER, userId.toString());
        }
        return new RateLimitSubject(TYPE_IP, request.getRemoteAddr());
    }
}
