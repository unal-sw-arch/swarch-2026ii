package com.parosurvivors.serviya.admin.infrastructure.dto.response;

import java.time.LocalDateTime;

/**
 * Respuesta web (Response) para la busqueda combinada de feedback del admin (RF-048).
 */
public record AdminFeedbackSearchResponse(
        String feedbackType,
        Long feedbackId,
        Long requestId,
        Long authorId,
        Long targetId,
        Integer rating,
        String comment,
        LocalDateTime createdAt) {
}
