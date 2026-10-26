package com.parosurvivors.serviya.shared.events.domain;

import java.util.List;

/**
 * Se revirtió (borró) el feedback del cliente a un servicio — por moderación o al pasar la
 * solicitud a NO_PRESTADA. Produce el efecto inverso a {@link ServiceFeedbackSubmittedEvent}:
 * los listeners decrementan totales y recalculan promedios (sin bajar de 0). Mismo payload
 * autocontenido para no reconsultar la BD.
 */
public record ServiceFeedbackRevertedEvent(
        Long requestId,
        Long serviceId,
        Long offererId,
        Long clientId,
        Integer rating,
        boolean hasComment,
        List<TagRef> tags) {
}
