package com.parosurvivors.notifications.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Notificación generada por el sistema (registro base, sin secretos). Mapea la tabla
 * {@code notifications}. Los datos sensibles del mensaje final ({@code protectedData})
 * NO se persisten aquí; los arma el adaptador de cada canal.
 *
 * <p>Cambios respecto al monolito: {@code userId} ya no es FK a {@code users} (esa tabla es de identity);
 * {@code sourceEventId} es el id del mensaje que la originó (clave de idempotencia del consumidor) y
 * {@code recipientEmail} es la instantánea del correo del destinatario que trae el mensaje (se guarda
 * cifrada para que el reintento programado pueda reenviar sin consultar a identity).</p>
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Notification {
    private Long id;
    private String sourceEventId;
    private Long userId;
    private String notificationType;
    private String title;
    private String message;
    private String entityType;
    private Long entityId;
    private String recipientEmail;
    private LocalDateTime createdAt;

    // =====================================================
    // BUSINESS METHODS
    // =====================================================

    public boolean referencesEntity() {
        return entityType != null && entityId != null;
    }

    public boolean hasRecipientEmail() {
        return recipientEmail != null && !recipientEmail.isBlank();
    }
}
