package com.parosurvivors.serviya.users.application.dto.result;

import com.parosurvivors.serviya.users.domain.RoleName;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Salida de aplicacion (Result) del flujo de autenticacion. Lo que devuelven los casos de uso
 * login/register/refresh: el access token emitido y metadatos, mas el refresh token (Fase 2).
 * NO es un tipo web; el WebMapper traduce {@code token}/{@code expiresAt} a AuthResponse y el controller
 * pone {@code refreshToken} en una cookie HttpOnly (nunca en el cuerpo).
 *
 * <p>{@code refreshToken} y {@code refreshExpiresAt} pueden ser {@code null} cuando el flujo no emite ni
 * rota el refresh (p. ej. {@code acquireRole}, que solo reemite el access y deja intacta la cookie).
 */
public record AuthResult(
        String token,
        Long userId,
        List<RoleName> roles,
        LocalDateTime expiresAt,
        String refreshToken,
        LocalDateTime refreshExpiresAt) {
}
