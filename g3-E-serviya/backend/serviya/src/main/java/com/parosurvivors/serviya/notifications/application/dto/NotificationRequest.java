package com.parosurvivors.serviya.notifications.application.dto;

import com.parosurvivors.serviya.notifications.domain.ChannelName;

import java.time.Instant;
import java.util.Map;
import java.util.Set;

/**
 * Comando de notificación listo para salir por el outbox. {@code eventId} es único por petición: es la
 * clave de idempotencia con la que notifications-service descarta las entregas duplicadas del bus.
 *
 * @param recipientEmail correo del destinatario resuelto aquí (identity sigue en el monolito); null si no
 *                       se pidió EMAIL o el usuario no tiene correo.
 */
public record NotificationRequest(
        String eventId,
        Instant occurredAt,
        Long userId,
        String type,
        String title,
        String message,
        String entityType,
        Long entityId,
        Set<ChannelName> channels,
        String recipientEmail,
        Map<String, String> protectedData) {
}
