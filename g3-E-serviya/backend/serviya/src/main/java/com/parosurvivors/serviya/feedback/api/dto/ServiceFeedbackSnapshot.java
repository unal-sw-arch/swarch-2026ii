package com.parosurvivors.serviya.feedback.api.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Feedback del cliente sobre un servicio.
 */
public record ServiceFeedbackSnapshot(
        Long id,
        Long requestId,
        Long serviceId,
        Long clientId,
        Integer rating,
        String comment,
        List<Long> tagIds,
        LocalDateTime createdAt) {

    public ServiceFeedbackSnapshot {
        tagIds = tagIds == null ? List.of() : List.copyOf(tagIds);
    }
}
