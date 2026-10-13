package com.parosurvivors.serviya.feedback.infrastructure.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Feedback del oferente al cliente (rating + reseña unificados). Mapea {@code client_feedback}.
 * {@code offererId} = autor, {@code clientId} = calificado. {@code rating} y {@code comment}
 * son nullable (al menos uno presente, validado por CHECK en BD).
 */
@Entity
@Table(name = "client_feedback")
@Getter
@Setter
public class ClientFeedbackEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    private Integer version;

    @Column(name = "request_id", nullable = false, unique = true)
    private Long requestId;

    @Column(name = "offerer_id", nullable = false)
    private Long offererId;

    @Column(name = "client_id", nullable = false)
    private Long clientId;

    @Column
    private Integer rating;

    @Column(columnDefinition = "TEXT")
    private String comment;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
