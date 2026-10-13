package com.parosurvivors.notifications.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Entrega de una notificación por un canal (estado de envío + lectura).
 * Mapea la tabla {@code notification_deliveries}.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationDelivery {
    private Long id;
    /** Bloqueo optimista (B10). Se conserva del registro leído para que el update lo verifique. */
    private Integer version;
    private Long notificationId;
    private Integer channelId;
    private DeliveryStatus deliveryStatus;
    private LocalDateTime readAt;
    private LocalDateTime sentAt;
    /** Número de intentos de envío realizados (el primer envío cuenta como 1). Lo usa el reintento programado. */
    private Integer attempts;

    // =====================================================
    // BUSINESS METHODS
    // =====================================================

    public boolean isRead() {
        return deliveryStatus == DeliveryStatus.READ || readAt != null;
    }

    /** Contabiliza un intento de envío (tolerante a null para filas antiguas sin el contador). */
    public void registerAttempt() {
        this.attempts = (this.attempts == null ? 0 : this.attempts) + 1;
    }

    public void markAsSent() {
        this.deliveryStatus = DeliveryStatus.SENT;
        this.sentAt = LocalDateTime.now();
    }

    public void markAsFailed() {
        this.deliveryStatus = DeliveryStatus.FAILED;
    }

    public void markAsRead() {
        if (isRead()) {
            return;
        }
        this.deliveryStatus = DeliveryStatus.READ;
        this.readAt = LocalDateTime.now();
    }
}
