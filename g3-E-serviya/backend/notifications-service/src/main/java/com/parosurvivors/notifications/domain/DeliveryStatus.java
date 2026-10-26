package com.parosurvivors.notifications.domain;

/**
 * Estado de entrega de una notificación por un canal. Coincide con el ENUM de la columna
 * {@code notification_deliveries.delivery_status}.
 */
public enum DeliveryStatus {
    PENDING,
    SENT,
    FAILED,
    READ
}
