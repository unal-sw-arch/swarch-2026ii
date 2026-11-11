package com.parosurvivors.serviya.shared.audit;

/**
 * Caso de uso de auditoría de seguridad (T6.1): lo llaman los servicios de auth para registrar un evento.
 * Enriquece con IP/user agent/correlationId de la petición y la fecha; el llamador solo aporta el tipo, el
 * usuario (si lo hay), el resultado y un detalle corto (sin datos sensibles).
 */
public interface SecurityAuditServicePort {

    void record(AuditEventType eventType, Long userId, boolean success, String detail);

    /** Atajo para eventos de resultado exitoso sin detalle. */
    default void record(AuditEventType eventType, Long userId) {
        record(eventType, userId, true, null);
    }
}
