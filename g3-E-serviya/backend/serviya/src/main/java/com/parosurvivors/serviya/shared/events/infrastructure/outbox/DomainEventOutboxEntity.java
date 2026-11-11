package com.parosurvivors.serviya.shared.events.infrastructure.outbox;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/**
 * Fila del outbox de eventos de integracion (requests/users hacia el chat).
 *
 * <p>Se inserta en la transaccion del caso de uso y la borra el relay cuando RabbitMQ confirma.
 * Tabla separada de {@code notification_outbox} para no mezclar el comando de notificaciones
 * (con payload cifrado) con el envelope de dominio.</p>
 */
@Entity
@Table(name = "domain_event_outbox")
@Getter
@Setter
public class DomainEventOutboxEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false, unique = true, length = 36)
    private String eventId;

    @Column(name = "exchange_name", nullable = false, length = 120)
    private String exchange;

    @Column(name = "routing_key", nullable = false, length = 160)
    private String routingKey;

    @Column(name = "payload", nullable = false, columnDefinition = "MEDIUMTEXT")
    private String payload;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "attempts", nullable = false, columnDefinition = "INT UNSIGNED NOT NULL DEFAULT 0")
    private Integer attempts;

    @Column(name = "last_error", length = 500)
    private String lastError;
}
