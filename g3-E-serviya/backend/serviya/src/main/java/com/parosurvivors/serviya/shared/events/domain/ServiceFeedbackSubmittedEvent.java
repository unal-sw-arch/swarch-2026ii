package com.parosurvivors.serviya.shared.events.domain;

import java.util.List;

/**
 * El cliente envió feedback (rating y/o comentario + tags) sobre un servicio prestado.
 * Evento autocontenido: transporta los objetivos de métricas ({@code serviceId}, {@code offererId})
 * y los datos ya resueltos (rating opcional, si hay comentario, tags con sentimiento) para que los
 * listeners actualicen service_metrics, service_tag_metrics, offerer_metrics y offerer_tag_metrics
 * sin consultar la BD.
 *
 * @param rating   calificación 1-5, o {@code null} si el feedback fue solo comentario/tags.
 * @param hasComment true si el feedback trae comentario (alimenta totalComments).
 * @param tags     etiquetas del feedback con su sentimiento (vacía si no hubo).
 */
public record ServiceFeedbackSubmittedEvent(
        Long requestId,
        Long serviceId,
        Long offererId,
        Long clientId,
        Integer rating,
        boolean hasComment,
        List<TagRef> tags) {
}
