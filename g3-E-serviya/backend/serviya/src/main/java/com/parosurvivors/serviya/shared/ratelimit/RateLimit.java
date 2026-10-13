package com.parosurvivors.serviya.shared.ratelimit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marca un endpoint con una cuota de nivel 2 (estricta, por endpoint). El {@link RateLimitInterceptor}
 * resuelve el sujeto de forma estándar ({@link RateLimitSubject}: usuario autenticado o IP) y consume de
 * la política nombrada. La política vive junto al endpoint (no en un mapa central que se desincroniza).
 *
 * <p>Ejemplo: {@code @RateLimit("request-write")} sobre {@code POST /service-requests}.</p>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RateLimit {

    /** Nombre de la política en {@code app.ratelimit.policies.<value>}. */
    String value();
}
