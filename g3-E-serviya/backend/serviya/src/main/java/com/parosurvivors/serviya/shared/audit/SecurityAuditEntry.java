package com.parosurvivors.serviya.shared.audit;

import java.time.LocalDateTime;

/**
 * Entrada de auditoría a persistir (T6.1). {@code userId} puede ser nulo (p. ej. login fallido de un correo
 * inexistente). {@code ip}/{@code userAgent}/{@code correlationId} salen del contexto de la petición
 * ({@link SecurityAuditContext}); nunca se registran tokens ni contraseñas (regla #5).
 */
public record SecurityAuditEntry(
        AuditEventType eventType,
        Long userId,
        boolean success,
        String detail,
        String ip,
        String userAgent,
        String correlationId,
        LocalDateTime occurredAt) {
}
