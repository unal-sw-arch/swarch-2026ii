package com.parosurvivors.serviya.feedback.infrastructure.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/**
 * Tabla puente {@code client_feedback_tags} (etiquetas asignadas a un feedback de cliente).
 */
@Entity
@Table(
        name = "client_feedback_tags",
        uniqueConstraints = @UniqueConstraint(name = "uq_client_feedback_tag", columnNames = {"feedback_id", "tag_id"})
)
@Getter
@Setter
public class ClientFeedbackTagEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    private Integer version;

    @Column(name = "feedback_id", nullable = false)
    private Long feedbackId;

    @Column(name = "tag_id", nullable = false)
    private Long tagId;
}
