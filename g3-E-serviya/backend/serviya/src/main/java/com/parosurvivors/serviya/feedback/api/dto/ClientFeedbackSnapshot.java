package com.parosurvivors.serviya.feedback.api.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Feedback del oferente sobre un cliente.
 */
public record ClientFeedbackSnapshot(
        Long id,
        Long requestId,
        Long offererId,
        Long clientId,
        Integer rating,
        String comment,
        List<Long> tagIds,
        LocalDateTime createdAt) {

    public ClientFeedbackSnapshot {
        tagIds = tagIds == null ? List.of() : List.copyOf(tagIds);
    }
}
