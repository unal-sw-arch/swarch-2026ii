package com.parosurvivors.serviya.shared.exceptions;

import lombok.Getter;

/**
 * Lanzada cuando se agota la cuota de peticiones de un endpoint protegido por rate limiting.
 * El {@code GlobalExceptionHandler} la mapea a HTTP 429 junto con la cabecera {@code Retry-After}.
 *
 * <p>El mensaje es deliberadamente vago y NUNCA menciona el recurso concreto: en los endpoints de
 * recuperación de contraseña la cuota se lleva también por correo, y un mensaje del estilo "demasiados
 * intentos para este correo" convertiría el propio límite en el oráculo de enumeración que el resto del
 * flujo se esfuerza en evitar.</p>
 */
@Getter
public class RateLimitExceededException extends RuntimeException {

    /** Segundos que faltan para que se recupere cuota; viaja al cliente en {@code Retry-After}. */
    private final long retryAfterSeconds;

    public RateLimitExceededException(long retryAfterSeconds) {
        super("Demasiadas peticiones. Intentalo de nuevo mas tarde.");
        this.retryAfterSeconds = retryAfterSeconds;
    }
}
