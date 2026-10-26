package com.parosurvivors.notifications.application.dto.result;

import java.time.LocalDateTime;

/**
 * Salida de aplicacion (Result) de una notificacion entregada (CQRS-light). Vista agregada que combina
 * NotificationDelivery + Notification; no pasa por una unica entidad de dominio.
 * Lo devuelve NotificationDeliveryService.getDeliveries; el WebMapper lo traduce a Response.
 */
public record NotificationDeliveryResult(
        Long deliveryId,
        Long notificationId,
        Integer channelId,
        String deliveryStatus,
        LocalDateTime readAt,
        LocalDateTime sentAt,
        // datos de la notificacion
        String notificationType,
        String title,
        String message,
        String entityType,
        Long entityId,
        LocalDateTime createdAt) {
}
