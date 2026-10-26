package com.parosurvivors.serviya.feedback.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Feedback del cliente a un servicio: unifica la calificación (1-5, opcional) y la reseña
 * (comentario + etiquetas, opcional) en una sola entidad. Mapea la tabla {@code service_feedback}
 * (una por solicitud). {@code rating} y {@code comment} son nullable; al menos uno debe venir
 * presente. {@code tagIds} corresponde a la tabla puente {@code service_feedback_tags}; no es una
 * columna de service_feedback, por eso el mapper de persistencia lo ignora.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ServiceFeedback {
    private Long id;
    private Long requestId;
    private Long clientId;
    /** Servicio reseñado, denormalizado desde la solicitud para listar reseñas por servicio sin JOIN. */
    private Long serviceId;
    private Integer rating;
    private String comment;
    private LocalDateTime createdAt;

    /** Ids del catálogo de etiquetas asociadas (persistidos en service_feedback_tags). */
    @Builder.Default
    private List<Long> tagIds = new ArrayList<>();

    // =====================================================
    // BUSINESS METHODS
    // =====================================================

    public boolean hasRating() {
        return rating != null;
    }

    public boolean hasComment() {
        return comment != null && !comment.isBlank();
    }

    public boolean hasTags() {
        return tagIds != null && !tagIds.isEmpty();
    }

    /** Sin calificación ni comentario: no hay nada que registrar. */
    public boolean isEmpty() {
        return !hasRating() && !hasComment();
    }

    /** La calificación es válida si está ausente o dentro del rango 1-5. */
    public boolean isRatingValid() {
        return rating == null || (rating >= 1 && rating <= 5);
    }
}
