package com.parosurvivors.serviya.notifications.domain;

/**
 * Nombres canónicos de los canales de entrega. Forman parte del CONTRATO del mensaje
 * {@code notification.requested} (campo {@code channels}): cada valor debe existir en el catálogo
 * {@code notification_channels} de notifications-service, que es quien resuelve el id real.
 */
public enum ChannelName {
    INTERNAL,
    EMAIL
}
