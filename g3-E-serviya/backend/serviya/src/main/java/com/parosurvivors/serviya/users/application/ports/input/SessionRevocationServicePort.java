package com.parosurvivors.serviya.users.application.ports.input;

/**
 * Caso de uso de revocación total de la sesión de un usuario (Fase 2, Axis B). Reúne las dos palancas de
 * revocación en una sola operación idempotente, para que cada evento del ciclo de vida del plan (P2.5
 * — baneo, eliminación, quita de rol, cambio/reset de contraseña, logout-all) la invoque sin duplicar
 * lógica:
 *
 * <ol>
 *   <li>revoca <b>todas las familias de refresh</b> del usuario en BD (garantía durable: no puede
 *       renovar el access en ningún dispositivo);</li>
 *   <li>escribe {@code min_iat} en el sustrato de revocación (el gateway invalida de inmediato los
 *       access tokens cortos ya emitidos, sin esperar a que expiren).</li>
 * </ol>
 */
public interface SessionRevocationServicePort {

    /** Revoca todas las sesiones del usuario: familias de refresh (BD) + {@code min_iat} (Redis). */
    void revokeAllSessions(Long userId);
}
