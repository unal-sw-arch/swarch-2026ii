package com.parosurvivors.serviya.shared.audit;

/**
 * Contexto por petición (IP, user agent, correlationId) para enriquecer la auditoría sin pasar el
 * {@code HttpServletRequest} por todas las capas. Lo rellena {@link AuditContextFilter} al entrar la
 * petición y lo limpia al salir (ThreadLocal; el modelo de hilo-por-petición del monolito servlet lo hace
 * seguro). Fuera de una petición (jobs @Scheduled) devuelve nulos.
 */
public final class SecurityAuditContext {

    private record Ctx(String ip, String userAgent, String correlationId) {
    }

    private static final ThreadLocal<Ctx> HOLDER = new ThreadLocal<>();

    private SecurityAuditContext() {
    }

    public static void set(String ip, String userAgent, String correlationId) {
        HOLDER.set(new Ctx(ip, userAgent, correlationId));
    }

    public static void clear() {
        HOLDER.remove();
    }

    public static String ip() {
        Ctx c = HOLDER.get();
        return c == null ? null : c.ip();
    }

    public static String userAgent() {
        Ctx c = HOLDER.get();
        return c == null ? null : c.userAgent();
    }

    public static String correlationId() {
        Ctx c = HOLDER.get();
        return c == null ? null : c.correlationId();
    }
}
