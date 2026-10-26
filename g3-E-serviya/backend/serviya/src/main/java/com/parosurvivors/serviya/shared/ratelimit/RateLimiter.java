package com.parosurvivors.serviya.shared.ratelimit;

/**
 * Limitador de peticiones distribuido (deuda #6 / M13). Reemplaza el contador en memoria por un
 * algoritmo atómico sobre {@code redis-coord}, de modo que la cuota se comparte entre ≥2 instancias.
 * Detrás de esta interfaz hay scripts Lua versionados (token bucket y ventana deslizante), reutilizables
 * e interoperables entre lenguajes (ADR / convención §3 de la guía de Redis).
 *
 * <p>El "sujeto" es quién consume la cuota, estandarizado (D-09): {@code userId} para endpoints
 * autenticados, {@code ip} para anónimos, {@code email} solo donde el ataque va dirigido a una cuenta
 * alcanzable sin login (password-reset). La clave Redis es
 * {@code rl:v1:{<subjectType>:<subjectId>}:<policy>} (hash tag para Cluster).</p>
 *
 * <p><b>Modo de fallo (D-07):</b> si Redis no responde, la decisión la dicta la política: {@code OPEN}
 * deja pasar (prioriza disponibilidad) y {@code CLOSED} rechaza (prioriza protección). En ambos casos se
 * registra una métrica {@code error_open}/{@code error_closed}.</p>
 */
public interface RateLimiter {

    /**
     * Intenta consumir {@code tokens} de la cuota {@code policy} para el sujeto dado.
     *
     * @param policy      nombre de la política configurada en {@code app.ratelimit.policies.<policy>}.
     * @param subjectType tipo de sujeto: {@code "ip"}, {@code "user"}, {@code "email"}.
     * @param subjectId   identificador del sujeto (IP, id de usuario, correo…).
     * @param tokens      cantidad a consumir (normalmente 1).
     * @return la decisión; nunca {@code null}. Si la política no existe, se permite y se registra un warn.
     */
    RateLimitDecision tryConsume(String policy, String subjectType, String subjectId, int tokens);

    /** Atajo para consumir un solo token. */
    default RateLimitDecision tryConsume(String policy, String subjectType, String subjectId) {
        return tryConsume(policy, subjectType, subjectId, 1);
    }
}
