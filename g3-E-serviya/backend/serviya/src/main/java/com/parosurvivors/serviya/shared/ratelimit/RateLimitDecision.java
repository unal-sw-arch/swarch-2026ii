package com.parosurvivors.serviya.shared.ratelimit;

import java.time.Duration;

/**
 * Resultado de evaluar una cuota contra el {@link RateLimiter}.
 *
 * @param allowed    {@code true} si la petición puede continuar; {@code false} si se agotó la cuota
 *                   (o si Redis falló y la política es {@code CLOSED}).
 * @param remaining  tokens que quedan en el bucket tras esta evaluación (0 si se rechazó o no se sabe).
 * @param retryAfter tiempo que el cliente debería esperar antes de reintentar (para la cabecera
 *                   {@code Retry-After}); {@link Duration#ZERO} si se permitió.
 */
public record RateLimitDecision(boolean allowed, long remaining, Duration retryAfter) {

    /** Decisión de permitido con los restantes indicados y sin espera. */
    public static RateLimitDecision allowed(long remaining) {
        return new RateLimitDecision(true, Math.max(0, remaining), Duration.ZERO);
    }

    /** Decisión de rechazo con el tiempo de espera sugerido. */
    public static RateLimitDecision rejected(long remaining, Duration retryAfter) {
        return new RateLimitDecision(false, Math.max(0, remaining), retryAfter);
    }

    /** Segundos para la cabecera {@code Retry-After} (redondeo hacia arriba, mínimo 1 si hay espera). */
    public long retryAfterSeconds() {
        long millis = retryAfter.toMillis();
        if (millis <= 0) {
            return 0;
        }
        return Math.max(1, (millis + 999) / 1000);
    }
}
