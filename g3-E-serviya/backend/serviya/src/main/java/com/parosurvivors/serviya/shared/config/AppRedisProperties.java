package com.parosurvivors.serviya.shared.config;

import java.time.Duration;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuración de las conexiones a Redis (contrato §3.3 de la guía, {@code documents/redis/}).
 *
 * <p>Dos nodos independientes por propósito (ADR-001): {@code coord} (locks; rate limit en Fase 3) y
 * {@code cache} (caché, Fase 6). En la adopción <b>Vía B</b> solo se cablea {@code coord} como bean
 * ({@link RedisCoordConfig}); el bloque {@code cache} existe aquí desde ya para fijar el contrato de
 * configuración sin cambiarlo después, pero todavía no crea ninguna conexión.</p>
 *
 * <p>El campo {@code mode} y la bandera {@code ssl} existen desde la Fase 1 aunque solo se implemente
 * {@code standalone}; sentinel/cluster y TLS llegan en Fases 8/7 sin tocar este contrato.</p>
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "app.redis")
public class AppRedisProperties {

    private Node coord = new Node();
    private Node cache = new Node();

    @Getter
    @Setter
    public static class Node {
        /** standalone | sentinel | cluster. Solo 'standalone' implementado (Fase 1). */
        private String mode = "standalone";
        private String host = "localhost";
        private int port = 6379;
        /** host:port,host:port — para sentinel/cluster (Fase 8). */
        private String nodes = "";
        private String sentinelMaster = "";
        private String username = "";
        private String password = "";
        private boolean ssl = false;
        private Duration timeout = Duration.ofSeconds(2);
        private Duration connectTimeout = Duration.ofSeconds(2);
        /**
         * Timeout de comando del <b>hot-path</b> (rate limiting, en cada petición): mucho más corto que
         * {@link #timeout} (locks de schedulers, en background) para no amplificar la latencia si
         * {@code redis-coord} se pone lento. Lo usa {@code coordHotRedisTemplate} (Fase 4, D-07/D-08).
         */
        private Duration hotCommandTimeout = Duration.ofMillis(300);
    }
}
