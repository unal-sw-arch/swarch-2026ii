package com.parosurvivors.notifications.infrastructure.messaging;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.Instant;
import java.util.Map;
import java.util.Set;

/**
 * CONTRATO v1 del mensaje {@code notification.requested} (JSON) que publica el outbox del monolito.
 * Copia gemela en el monolito; documentado en documents/microservicios/notifications-service.md.
 *
 * <p>Reglas de evolución: añadir campos opcionales es compatible (se ignoran los desconocidos); cambiar o
 * quitar campos exige {@code schemaVersion} nuevo y routing key nueva ({@code notification.requested.v2}).</p>
 *
 * @param sealed {@code base64} AES-GCM de {@link SealedContent} (correo + protectedData); null si no hay nada sensible.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
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

    /** Contenido sellado del mensaje. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SealedContent(String recipientEmail, Map<String, String> protectedData) {
    }
}
