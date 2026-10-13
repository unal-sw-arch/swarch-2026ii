package com.parosurvivors.serviya.feedback.infrastructure.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Salida web (Response) del feedback de cliente (rating + resena con tags, emparejados).
 * Mapea desde ClientFeedbackResult.
 * TODO: revisar campos.
 */
@Schema(description = "Feedback recibido por un cliente")
public record ClientFeedbackResponse(
        Long requestId,
        Long clientId,
        Long offererId,
        Integer rating,
        String comment,
        List<String> tags,
        LocalDateTime createdAt) {
}
