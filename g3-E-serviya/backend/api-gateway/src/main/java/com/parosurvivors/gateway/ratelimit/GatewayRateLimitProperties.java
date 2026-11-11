package com.parosurvivors.gateway.ratelimit;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Políticas de rate limiting de auth por IP (ventana deslizante) portadas del monolito (T4.9/P3.7). El techo
 * global {@code api-default} lo sigue leyendo {@link ReactiveRedisRateLimiter} por {@code @Value}; aquí solo
 * viven las políticas de {@code /api/v1/auth/**} que mapea {@link AuthRateLimitWebFilter}.
 *
 * <pre>
 * serviya.ratelimit.auth:
 *   login-ip:    { capacity: 10, window: 15m, fail-open: true }
 *   register-ip: { capacity: 5,  window: 60m, fail-open: false }
 * </pre>
 */
@ConfigurationProperties("serviya.ratelimit")
public class GatewayRateLimitProperties {

    /** nombre-de-política → parámetros de la ventana deslizante. */
    private Map<String, SlidingPolicy> auth = new LinkedHashMap<>();

    public Map<String, SlidingPolicy> getAuth() {
        return auth;
    }

    public void setAuth(Map<String, SlidingPolicy> auth) {
        this.auth = auth;
    }

    public static class SlidingPolicy {
        /** Máximo de intentos dentro de la ventana. */
        private long capacity;
        /** Tamaño de la ventana deslizante. */
        private Duration window;
        /** OPEN (true): deja pasar si Redis cae. CLOSED (false): rechaza. */
        private boolean failOpen;

        public long getCapacity() {
            return capacity;
        }

        public void setCapacity(long capacity) {
            this.capacity = capacity;
        }

        public Duration getWindow() {
            return window;
        }

        public void setWindow(Duration window) {
            this.window = window;
        }

        public boolean isFailOpen() {
            return failOpen;
        }

        public void setFailOpen(boolean failOpen) {
            this.failOpen = failOpen;
        }
    }
}
