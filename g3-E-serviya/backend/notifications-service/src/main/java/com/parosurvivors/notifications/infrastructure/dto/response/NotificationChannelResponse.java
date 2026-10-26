package com.parosurvivors.notifications.infrastructure.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Salida web (Response) de un canal de notificacion. GET /api/v1/notification-channels.
 * Mapea desde el dominio NotificationChannel.
 */
@Schema(description = "Canal de notificacion disponible")
public record NotificationChannelResponse(
        Integer id,
        String name) {
}
