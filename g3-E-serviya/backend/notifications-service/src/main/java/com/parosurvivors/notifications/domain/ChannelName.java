package com.parosurvivors.notifications.domain;

/**
 * Nombres canónicos de los canales de entrega. Cada valor debe coincidir EXACTAMENTE con la columna
 * {@code name} de la tabla {@code notification_channels} (sembrada por Flyway como INTERNAL, EMAIL): así los
 * productores expresan la intención por nombre y {@code NotificationService} resuelve el id real en un
 * único lugar, sin depender del orden de inserción del seed. Es también parte del contrato del mensaje
 * {@code notification.requested} (campo {@code channels}).
 */
public enum ChannelName {
    INTERNAL,
    EMAIL
}
