package com.parosurvivors.serviya.notifications.infrastructure.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Fila del outbox transaccional de notificaciones (M3). Se inserta en la transacción de negocio y la borra
 * el relay en cuanto RabbitMQ confirma la publicación. El {@code payload} es el JSON final del mensaje (con
 * los datos sensibles ya sellados).
 */
@Entity
@Table(name = "notification_outbox")
@Getter
@Setter
public class NotificationOutboxEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false, unique = true, length = 36)
    private String eventId;

    @Column(name = "payload", nullable = false, columnDefinition = "MEDIUMTEXT")
    private String payload;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    /** Intentos de publicación fallidos (solo diagnóstico: el relay reintenta indefinidamente). */
    @Column(name = "attempts", nullable = false, columnDefinition = "INT UNSIGNED NOT NULL DEFAULT 0")
    private Integer attempts;

    @Column(name = "last_error", length = 500)
    private String lastError;
}
