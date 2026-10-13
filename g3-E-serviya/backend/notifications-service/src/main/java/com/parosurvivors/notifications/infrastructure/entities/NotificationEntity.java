package com.parosurvivors.notifications.infrastructure.entities;

import com.parosurvivors.notifications.infrastructure.security.PiiAttributeConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "notifications")
@Getter
@Setter
public class NotificationEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    private Integer version;

    /** Id del mensaje del bus que la originó. UNIQUE: clave de idempotencia del consumidor. */
    @Column(name = "source_event_id", length = 36, unique = true)
    private String sourceEventId;

    /** Id del usuario en identity (monolito). Sin FK: la tabla users vive en otra base de datos. */
    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "notification_type", nullable = false, length = 100)
    private String notificationType;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String message;

    @Column(name = "entity_type", length = 100)
    private String entityType;

    @Column(name = "entity_id")
    private Long entityId;

    /** Correo del destinatario (instantánea del mensaje), cifrado AES-256-GCM en reposo (PII, RNF-005). */
    @Convert(converter = PiiAttributeConverter.class)
    @Column(name = "recipient_email", columnDefinition = "VARBINARY(512)")
    private String recipientEmail;

    @Column(name = "created_at")
    private LocalDateTime createdAt;
}
