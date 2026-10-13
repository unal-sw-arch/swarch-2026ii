package com.parosurvivors.serviya.shared.events.domain;

import java.util.List;

/**
 * El oferente envió feedback (rating y/o comentario + tags) sobre un cliente. Evento autocontenido
 * que alimenta client_metrics y client_tag_metrics. {@code offererId} es el autor y {@code clientId}
 * el calificado (objetivo de las métricas).
 *
 * @param rating     calificación 1-5, o {@code null} si fue solo comentario/tags.
 * @param hasComment true si el feedback trae comentario (alimenta totalComments).
 * @param tags       etiquetas del feedback con su sentimiento (vacía si no hubo).
 */
public record ClientFeedbackSubmittedEvent(
        Long requestId,
        Long clientId,
        Long offererId,
        Integer rating,
        boolean hasComment,
        List<TagRef> tags) {
}
