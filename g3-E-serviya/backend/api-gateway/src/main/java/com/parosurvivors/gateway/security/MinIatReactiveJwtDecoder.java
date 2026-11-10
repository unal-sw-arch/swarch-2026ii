package com.parosurvivors.gateway.security;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.reactor.circuitbreaker.operator.CircuitBreakerOperator;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtValidationException;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import reactor.core.publisher.Mono;

/**
 * Decorador del {@link ReactiveJwtDecoder} que aplica la revocación inmediata {@code min_iat} (Fase 3,
 * T3.5). Primero delega la validación estándar (firma ES256, {@code iss}/{@code aud}/timestamps); si el
 * token es válido, consulta {@code auth:min_iat:{sub}} en {@code redis-coord} y, si
 * {@code token.iat < min_iat}, lo rechaza con {@link JwtValidationException} — así el 401 sale por el flujo
 * estándar de Spring Security (contrato 4.3). Se implementa como decorador del decoder, no como
 * {@code OAuth2TokenValidator}, porque la consulta a Redis es reactiva (no bloqueante, regla #7).
 *
 * <p><b>Circuit breaker + fail-open (P3.1):</b> la consulta va envuelta en un {@link CircuitBreaker}
 * Resilience4j. Mientras {@code redis-coord} responde, el breaker está cerrado y el chequeo es normal. Si
 * empieza a fallar (caído/timeout), el breaker <b>abre</b> y las siguientes consultas se cortan al instante
 * (la fuente va en {@link Mono#defer}, así ni se intenta la conexión): el gateway deja de pagar el timeout
 * en cada request. En ambos casos (error o breaker abierto) se hace <b>fail-open</b> —se deja pasar el
 * token— con una métrica de alerta. La ventana máxima de exposición es la vida del access token
 * ({@code access-ttl}), porque caduca solo; la revocación durable (familias de refresh en BD) no depende de
 * esto. Rechazar todo el tráfico autenticado por un parpadeo de Redis sería peor.</p>
 */
public class MinIatReactiveJwtDecoder implements ReactiveJwtDecoder {

    private static final Logger log = LoggerFactory.getLogger(MinIatReactiveJwtDecoder.class);
    private static final String KEY_PREFIX = "auth:min_iat:";
    private static final String METRIC = "gateway.min_iat.check";
    private static final String LATENCY_METRIC = "gateway.min_iat.latency";
    // Fallos del decoder delegado (firma ES256, iss/aud/timestamps y, en especial, errores al OBTENER el
    // JWKS del monolito): todos afloran aquí. No incluye la revocación min_iat (va aparte en METRIC).
    private static final String DECODE_METRIC = "gateway.jwt.decode";

    private final ReactiveJwtDecoder delegate;
    private final ReactiveStringRedisTemplate redis;
    private final MeterRegistry meters;
    private final CircuitBreaker breaker;

    public MinIatReactiveJwtDecoder(ReactiveJwtDecoder delegate,
                                    ReactiveStringRedisTemplate redis,
                                    MeterRegistry meters,
                                    CircuitBreaker breaker) {
        this.delegate = delegate;
        this.redis = redis;
        this.meters = meters;
        this.breaker = breaker;
    }

    @Override
    public Mono<Jwt> decode(String token) {
        return delegate.decode(token)
                // Firma/iss/aud/timestamps inválidos o JWKS inalcanzable: se cuenta como error de decodificación.
                .doOnError(ex -> meters.counter(DECODE_METRIC, "result", "error").increment())
                .flatMap(this::enforceMinIat);
    }

    private Mono<Jwt> enforceMinIat(Jwt jwt) {
        Instant issuedAt = jwt.getIssuedAt();
        String subject = jwt.getSubject();
        if (issuedAt == null || subject == null) {
            // Sin iat/sub no se puede evaluar; los validadores estándar ya cubren los tokens malformados.
            return Mono.just(jwt);
        }
        // Latencia del chequeo min_iat (consulta a redis-coord, incluido el corte inmediato por breaker abierto).
        Timer.Sample sample = Timer.start(meters);
        // defer: si el breaker está ABIERTO, corta antes de construir/suscribir la consulta a Redis.
        return Mono.defer(() -> redis.opsForValue().get(KEY_PREFIX + subject))
                .transformDeferred(CircuitBreakerOperator.of(breaker))
                .flatMap(value -> {
                    long minIat = Long.parseLong(value.trim());
                    if (issuedAt.getEpochSecond() < minIat) {
                        count("revoked");
                        return Mono.<Jwt>error(revoked());
                    }
                    count("allowed");
                    return Mono.just(jwt);
                })
                // Clave ausente: no hay revocación vigente para este usuario.
                .switchIfEmpty(Mono.fromSupplier(() -> {
                    count("no-key");
                    return jwt;
                }))
                .onErrorResume(ex -> {
                    if (ex instanceof JwtValidationException revoked) {
                        return Mono.error(revoked); // la revocación real no se traga
                    }
                    // Fail-open (P3.1): error de Redis o breaker ABIERTO ⇒ se deja pasar YA, con alerta.
                    boolean open = ex instanceof CallNotPermittedException;
                    count(open ? "breaker-open" : "error");
                    if (open) {
                        log.warn("min_iat omitido: el breaker de redis-coord está abierto; fail-open (usuario {})", subject);
                    } else {
                        log.warn("min_iat no disponible (redis-coord); fail-open para el usuario {}", subject, ex);
                    }
                    return Mono.just(jwt);
                })
                .doFinally(signal -> sample.stop(meters.timer(LATENCY_METRIC)));
    }

    private JwtValidationException revoked() {
        OAuth2Error error = new OAuth2Error(
                "invalid_token", "El token fue revocado (min_iat)",
                "https://tools.ietf.org/html/rfc6750#section-3.1");
        return new JwtValidationException("Token revocado por min_iat", List.of(error));
    }

    private void count(String result) {
        meters.counter(METRIC, "result", result).increment();
    }
}
