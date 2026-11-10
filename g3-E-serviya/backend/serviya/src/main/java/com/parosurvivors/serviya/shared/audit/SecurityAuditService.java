package com.parosurvivors.serviya.shared.audit;

import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Registra eventos de auditoría de seguridad (T6.1), enriqueciéndolos con el contexto de la petición
 * ({@link SecurityAuditContext}). <b>Mejor esfuerzo:</b> un fallo al auditar NO rompe el flujo de negocio
 * (login, baneo, refresh…); se registra en el log y se continúa.
 */
@Component
@RequiredArgsConstructor
public class SecurityAuditService implements SecurityAuditServicePort {

    private static final Logger log = LoggerFactory.getLogger(SecurityAuditService.class);

    private final SecurityAuditPort auditPort;

    @Override
    public void record(AuditEventType eventType, Long userId, boolean success, String detail) {
        try {
            auditPort.record(new SecurityAuditEntry(
                    eventType, userId, success, detail,
                    SecurityAuditContext.ip(),
                    SecurityAuditContext.userAgent(),
                    SecurityAuditContext.correlationId(),
                    LocalDateTime.now()));
        } catch (RuntimeException ex) {
            log.warn("No se pudo registrar el evento de auditoría {} (userId={})", eventType, userId, ex);
        }
    }
}
