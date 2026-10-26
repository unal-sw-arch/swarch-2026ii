package com.parosurvivors.serviya.feedback.infrastructure.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Salida web (Response) del feedback de servicio (rating + resena con tags, emparejados).
 * Mapea desde ServiceFeedbackResult.
 * TODO: revisar campos.
 */
@Schema(description = "Feedback recibido por un servicio")
public record ServiceFeedbackResponse(
        Long requestId,
        Long serviceId,
        Long clientId,
        Integer rating,
        String comment,
        List<String> tags,
        LocalDateTime createdAt) {
}
