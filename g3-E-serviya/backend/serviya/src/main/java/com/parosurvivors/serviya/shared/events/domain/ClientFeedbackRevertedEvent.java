package com.parosurvivors.serviya.shared.events.domain;

import java.util.List;

/**
 * Se revirtió (borró) el feedback del oferente a un cliente. Efecto inverso a
 * {@link ClientFeedbackSubmittedEvent}: los listeners decrementan totales y recalculan promedios
 * de client_metrics / client_tag_metrics (sin bajar de 0).
 */
public record ClientFeedbackRevertedEvent(
        Long requestId,
        Long clientId,
        Long offererId,
        Integer rating,
        boolean hasComment,
        List<TagRef> tags) {
}
