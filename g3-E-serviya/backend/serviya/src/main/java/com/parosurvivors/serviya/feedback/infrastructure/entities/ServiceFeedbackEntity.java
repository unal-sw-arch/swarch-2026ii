package com.parosurvivors.serviya.feedback.infrastructure.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Feedback del cliente al servicio (rating + reseña unificados). Mapea {@code service_feedback}.
 * {@code rating} y {@code comment} son nullable (al menos uno presente, validado por CHECK en BD).
 */
@Entity
@Table(name = "service_feedback")
@Getter
@Setter
public class ServiceFeedbackEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    private Integer version;

    @Column(name = "request_id", nullable = false, unique = true)
    private Long requestId;

    @Column(name = "client_id", nullable = false)
    private Long clientId;

    /** Denormalizado desde la solicitud (service_requests.service_id) para listar reseñas por servicio sin JOIN. */
    @Column(name = "service_id", nullable = false)
    private Long serviceId;

    @Column
    private Integer rating;

    @Column(columnDefinition = "TEXT")
    private String comment;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
