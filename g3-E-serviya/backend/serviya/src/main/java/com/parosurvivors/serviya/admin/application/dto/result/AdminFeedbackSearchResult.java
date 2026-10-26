package com.parosurvivors.serviya.admin.application.dto.result;

import java.time.LocalDateTime;

/**
 * Result de aplicacion (read-model) para la busqueda combinada de feedback del admin.
 * Unifica service_feedback y client_feedback en una misma vista.
 */
public record AdminFeedbackSearchResult(
        String feedbackType,
        Long feedbackId,
        Long requestId,
        Long authorId,
        Long targetId,
        Integer rating,
        String comment,
        LocalDateTime createdAt) {
}
