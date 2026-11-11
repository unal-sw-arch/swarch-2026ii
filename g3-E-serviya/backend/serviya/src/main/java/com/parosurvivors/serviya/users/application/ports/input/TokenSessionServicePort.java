package com.parosurvivors.serviya.users.application.ports.input;

import com.parosurvivors.serviya.users.application.dto.result.AuthResult;
import com.parosurvivors.serviya.users.domain.RoleName;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Puerto de entrada del manejo de SESIÓN con refresh tokens (Fase 2). Abre una sesión (login/registro),
 * la rota (POST /auth/refresh) y la cierra (logout). El access token corto va en el cuerpo; el refresh,
 * en una cookie HttpOnly (lo cablea el controller).
 */
public interface TokenSessionServicePort {

    /** Abre una sesión nueva: emite access + un refresh de una familia nueva. Login y registro. */
    AuthResult startSession(Long userId, List<RoleName> roles);

    /**
     * Rota la sesión: valida el refresh, relee cuenta y roles y emite un access nuevo. Fuera de la ventana
     * de gracia rota también el refresh (uno nuevo en la misma familia). Reuso de un token ya rotado o
     * revocado ⇒ revoca la familia. Lanza {@code UnauthorizedException} (401) si el refresh no sirve.
     */
    AuthResult refresh(String rawRefreshToken, String userAgent, String ip);

    /** Cierra la sesión actual: revoca toda la familia del refresh presentado. Idempotente. */
    void logout(String rawRefreshToken);

    /** Mantenimiento: borra los refresh tokens ya expirados o revocados antes del corte. Devuelve cuántos. */
    int deleteExpiredOrRevoked(LocalDateTime cutoff);
}
