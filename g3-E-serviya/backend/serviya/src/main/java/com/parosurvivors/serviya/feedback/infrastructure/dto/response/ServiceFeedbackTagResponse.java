package com.parosurvivors.serviya.feedback.infrastructure.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Salida web (Response) de una etiqueta del catalogo de resenas de servicio. GET /api/v1/service-feedback-tags.
 * Mapea desde el dominio ServiceFeedbackTagCatalog.
 */
@Schema(description = "Etiqueta del catalogo de resenas de servicio")
public record ServiceFeedbackTagResponse(
        Long id,
        String tagName,
        @Schema(description = "Sentimiento: P (positivo) o N (negativo)") String sentiment) {
}
