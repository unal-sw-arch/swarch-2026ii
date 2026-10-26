package com.parosurvivors.notifications.application.dto.command;

import com.parosurvivors.notifications.domain.ChannelName;

import java.util.Map;
import java.util.Set;

/**
 * Entrada de aplicación de {@code NotificationServicePort.notify}: lo que antes eran los 8 parámetros de
 * la llamada en memoria del monolito, más lo que ahora aporta el mensaje.
 *
 * @param sourceEventId  id único del mensaje que la origina (clave de idempotencia; entrega ≥1 del bus).
 * @param recipientEmail correo del destinatario resuelto por el productor (puede ser null si no hay EMAIL).
 * @param protectedData  datos sensibles para armar el mensaje final (p. ej. enlace de reset). NO se persisten.
 */
public record NotifyCommand(
        String sourceEventId,
        Long userId,
        String type,
        String title,
        String message,
        String entityType,
        Long entityId,
        Set<ChannelName> channels,
        String recipientEmail,
        Map<String, String> protectedData) {

    public NotifyCommand {
        channels = channels == null ? Set.of() : Set.copyOf(channels);
        protectedData = protectedData == null ? Map.of() : Map.copyOf(protectedData);
    }
}
