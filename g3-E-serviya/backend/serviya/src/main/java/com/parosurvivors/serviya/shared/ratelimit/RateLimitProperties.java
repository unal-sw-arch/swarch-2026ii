package com.parosurvivors.serviya.shared.ratelimit;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Políticas de rate limiting, externalizadas a configuración ({@code app.ratelimit.*}) para poder
 * ajustarlas sin recompilar (convención de la guía: políticas en config, no en código). Cada política
 * elige su algoritmo, su límite y su modo de fallo.
 *
 * <pre>
 * app:
 *   ratelimit:
 *     enabled: true
 *     policies:
 *       api-default:   { algorithm: TOKEN_BUCKET,   capacity: 200, refill-per-second: 2,  failure-mode: OPEN }
 *       login-ip:      { algorithm: SLIDING_WINDOW, capacity: 10,  window: 15m,           failure-mode: OPEN }
 *       reset-email:   { algorithm: SLIDING_WINDOW, capacity: 3,   window: 60m,           failure-mode: CLOSED }
 * </pre>
 */
@Component
@Getter
@Setter
@ConfigurationProperties(prefix = "app.ratelimit")
public class RateLimitProperties {

    /** Interruptor general; apagarlo deja pasar todo (pruebas de carga / manuales). */
    private boolean enabled = true;

    /** Políticas por nombre. La clave del mapa es el nombre que referencian filtro/interceptor/@RateLimit. */
    private Map<String, Policy> policies = new HashMap<>();

    public Policy policy(String name) {
        return policies.get(name);
    }

    public enum Algorithm {
        /** Recarga continua; admite ráfaga + tasa sostenida. Para el techo global (api-default). */
        TOKEN_BUCKET,
        /** Ventana deslizante exacta, sin ráfaga de borde. Para auth y endpoints costosos. */
        SLIDING_WINDOW
    }

    public enum FailureMode {
        /** Si Redis cae, deja pasar (prioriza disponibilidad). */
        OPEN,
        /** Si Redis cae, rechaza (prioriza protección). */
        CLOSED
    }

    @Getter
    @Setter
    public static class Policy {
        /** Algoritmo del bucket. */
        private Algorithm algorithm = Algorithm.SLIDING_WINDOW;
        /** Máximo de peticiones (ventana) o de ráfaga (token bucket). */
        private long capacity = 100;
        /** Ventana deslizante (solo SLIDING_WINDOW). */
        private Duration window = Duration.ofMinutes(15);
        /** Tokens repuestos por segundo (solo TOKEN_BUCKET). */
        private double refillPerSecond = 1.0;
        /** Qué hacer si Redis no responde al evaluar esta política. */
        private FailureMode failureMode = FailureMode.OPEN;
    }
}
