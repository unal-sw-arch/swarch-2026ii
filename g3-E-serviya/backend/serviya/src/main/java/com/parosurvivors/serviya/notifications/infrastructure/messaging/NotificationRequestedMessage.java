package com.parosurvivors.serviya.notifications.infrastructure.messaging;

import java.time.Instant;
import java.util.Map;
import java.util.Set;

/**
 * CONTRATO v1 del mensaje {@code notification.requested} (JSON). Copia gemela del consumidor en
 * notifications-service; documentado en documents/microservicios/notifications-service.md.
 *
 * <p>Reglas de evolución: añadir campos opcionales es compatible; cambiar o quitar campos exige
 * {@code schemaVersion} nuevo y routing key nueva ({@code notification.requested.v2}).</p>
 *
 * @param sealed {@code base64} AES-GCM de {@link SealedContent}; null si no hay nada sensible que enviar.
 */
public record NotificationRequestedMessage(
        int schemaVersion,
        String eventId,
        Instant occurredAt,
        Long userId,
        String type,
        String title,
        String message,
        String entityType,
        Long entityId,
        Set<String> channels,
        String sealed) {

    public static final int SCHEMA_VERSION = 1;

    /** Contenido sellado: correo del destinatario + datos protegidos para armar el mensaje final. */
    public record SealedContent(String recipientEmail, Map<String, String> protectedData) {
    }
}
