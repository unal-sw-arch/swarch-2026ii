package com.parosurvivors.serviya.shared.lock;

import java.time.Duration;
import java.util.OptionalLong;

/**
 * Representa un lock distribuido adquirido. Es {@link AutoCloseable}: se libera al cerrarlo (ideal con
 * try-with-resources), siempre con compare-and-delete por token (nunca libera un lock ajeno).
 *
 * <p>El {@link #fencingToken() fencing token} solo está presente si el lock se adquirió con
 * {@link DistributedLock#tryAcquireFenced}. Los {@code @Scheduled} usan {@link DistributedLock#tryAcquire}
 * (sin fencing), por eso ahí viene vacío (ADR-003).</p>
 */
public interface LockHandle extends AutoCloseable {

    /** Recurso bloqueado (sin el prefijo {@code lock:} ni el hash tag). */
    String resource();

    /** Token único de esta adquisición (para compare-and-delete/renew). */
    String token();

    /**
     * Fencing token estrictamente creciente por recurso; presente solo si se adquirió con fencing.
     * Vacío para los locks de los schedulers (idempotentes, sin escritura protegida por-entidad).
     */
    OptionalLong fencingToken();

    /**
     * Renueva el TTL del lock si todavía pertenece a este token.
     *
     * @return {@code true} si se renovó; {@code false} si el lock ya se perdió (expiró o lo tomó otro).
     */
    boolean renew(Duration ttl);

    /** Libera el lock (compare-and-delete). No lanza excepción comprobada: seguro en un {@code finally}. */
    @Override
    void close();
}
