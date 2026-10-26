package com.parosurvivors.serviya.shared.lock;

import java.time.Duration;
import java.util.Optional;

/**
 * Lock distribuido para coordinar ≥2 instancias del backend (deuda #1). Protocolo propio
 * {@code SET NX PX} + Lua detrás de esta interfaz (ADR-003 / D-04), reutilizable y apto para
 * multi-lenguaje. Implementación: {@link RedisDistributedLock} sobre {@code redis-coord}.
 *
 * <p><b>Fallo cerrado (D-08):</b> si Redis no responde, {@link #tryAcquire}/{@link #tryAcquireFenced}
 * devuelven vacío (no se concede el lock) y registran el error; la sección crítica <b>no</b> se ejecuta.
 * Para los @Scheduled esto es lo deseable: mejor no ejecutar que ejecutar dos veces.</p>
 *
 * <p>El "recurso" es un identificador lógico sin prefijos (p. ej. {@code "sched:reject-expired-pending"}
 * o {@code "pedido:123"}); la clave Redis es {@code lock:{<recurso>}} (hash tag para Cluster).</p>
 */
public interface DistributedLock {

    /**
     * Intenta adquirir el lock del recurso <b>sin</b> fencing (exclusión mutua simple).
     *
     * @return el {@link LockHandle} si se adquirió; vacío si está ocupado o si Redis falló (fallo cerrado).
     */
    Optional<LockHandle> tryAcquire(String resource, Duration ttl);

    /**
     * Intenta adquirir el lock <b>con</b> fencing token (D-05), para secciones críticas por-entidad que
     * validan un token creciente en la escritura (p. ej. deuda B8). No lo usan los schedulers (ADR-003).
     *
     * @return el {@link LockHandle} (con {@link LockHandle#fencingToken()} presente) si adquirió; vacío si no.
     */
    Optional<LockHandle> tryAcquireFenced(String resource, Duration ttl);

    /**
     * Ejecuta {@code task} solo si se adquiere el lock, liberándolo siempre al terminar. Pensado para los
     * {@code @Scheduled}: solo una instancia corre la tarea; las demás la omiten.
     *
     * @return {@code true} si la tarea se ejecutó (se obtuvo el lock); {@code false} si estaba ocupado o
     *     Redis no respondió (en cuyo caso no se ejecuta nada, D-08).
     */
    default boolean runIfAcquired(String resource, Duration ttl, Runnable task) {
        Optional<LockHandle> handle = tryAcquire(resource, ttl);
        if (handle.isEmpty()) {
            return false;
        }
        try (LockHandle ignored = handle.get()) {
            task.run();
        }
        return true;
    }
}
