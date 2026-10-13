package com.parosurvivors.serviya.feedback.infrastructure.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/**
 * Tabla puente {@code service_feedback_tags} (etiquetas asignadas a un feedback de servicio).
 */
@Entity
@Table(
        name = "service_feedback_tags",
        uniqueConstraints = @UniqueConstraint(name = "uq_service_feedback_tag", columnNames = {"feedback_id", "tag_id"})
)
@Getter
@Setter
public class ServiceFeedbackTagEntity {
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
