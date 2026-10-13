package com.parosurvivors.notifications.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Canal de notificación disponible (ej. INTERNAL, EMAIL). Mapea la tabla
 * {@code notification_channels}.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationChannel {
    private Integer id;
    private String name;
}
