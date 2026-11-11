package com.parosurvivors.serviya.users.application.ports.output;

import java.time.Instant;

/**
 * Puerto de salida de revocación inmediata de access tokens (Fase 2, Axis B). Publica el umbral
 * {@code min_iat} por usuario en el sustrato de revocación ({@code redis-coord}); el <b>gateway</b> lo lee
 * y rechaza con 401 cualquier access token cuyo {@code iat} sea anterior (contrato 4.3 del plan de
 * centralización de auth).
 *
 * <p>El adaptador vive en infraestructura (Redis). La capa de aplicación solo conoce este puerto: no sabe
 * de claves, TTL ni del circuit breaker.</p>
 */
public interface TokenRevocationPort {

    /**
     * Marca que todo access token del usuario emitido <b>antes</b> de {@code cutoff} queda revocado. El
     * adaptador escribe {@code auth:min_iat:{userId} = cutoff} (epoch en segundos) con un TTL igual a la
     * vida máxima del access token más la tolerancia de reloj: pasado ese plazo ya no queda ningún token
     * anterior vivo que revocar, así que la clave se autolimpia.
     *
     * <p>Mejor esfuerzo: si el sustrato de revocación no responde, el adaptador lo registra y no propaga
     * el error. La garantía durable de cierre de sesión es la revocación de las familias de refresh en BD;
     * {@code min_iat} solo acorta la ventana del access token corto.</p>
     */
    void revokeAllIssuedBefore(Long userId, Instant cutoff);
}
