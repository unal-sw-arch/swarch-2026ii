package com.parosurvivors.serviya.users.application.services;

import com.parosurvivors.serviya.users.application.ports.input.SessionRevocationServicePort;
import com.parosurvivors.serviya.users.application.ports.output.RefreshTokenPort;
import com.parosurvivors.serviya.users.application.ports.output.TokenRevocationPort;
import java.time.Instant;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Revocación total de sesión (Fase 2, Axis B). Punto único que combina las dos palancas para que los
 * eventos de P2.5 (baneo, eliminación, quita de rol, cambio/reset de contraseña, logout-all) no dupliquen
 * lógica: primero revoca en BD todas las familias de refresh del usuario (garantía durable), luego escribe
 * {@code min_iat} para que el gateway invalide de inmediato los access tokens cortos aún vivos.
 *
 * <p><b>Orden deliberado:</b> la revocación en BD va primero porque es la autoritativa; el {@code min_iat}
 * es de mejor esfuerzo (si Redis no responde, el adaptador lo traga) y solo acorta la ventana del access
 * token corto. Así, aunque el sustrato de revocación falle, el usuario no puede renovar el access.</p>
 */
@Component
@RequiredArgsConstructor
public class SessionRevocationService implements SessionRevocationServicePort {

    private final RefreshTokenPort refreshTokenPort;
    private final TokenRevocationPort tokenRevocationPort;

    @Override
    @Transactional
    public void revokeAllSessions(Long userId) {
        LocalDateTime now = LocalDateTime.now();
        // (1) Garantía durable: ninguna familia de refresh sobrevive ⇒ no puede renovar el access.
        refreshTokenPort.revokeAllForUser(userId, now);
        // (2) Revocación inmediata del access corto: min_iat = ahora + 1 s (contrato 4.3). El +1 cubre los
        //     tokens emitidos en el mismo segundo del evento (la regla del gateway rechaza iat < min_iat).
        tokenRevocationPort.revokeAllIssuedBefore(userId, Instant.now().plusSeconds(1));
    }
}
