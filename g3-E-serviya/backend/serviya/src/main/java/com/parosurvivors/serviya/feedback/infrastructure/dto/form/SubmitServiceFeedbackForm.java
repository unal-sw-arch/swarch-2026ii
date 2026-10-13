package com.parosurvivors.serviya.feedback.infrastructure.dto.form;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import java.util.List;

/**
 * Entrada web (Form) del feedback de un cliente a un servicio. POST /api/v1/service-requests/{id}/feedback
 * (RF-041, RF-045). Un solo envio agrupa rating + resena; cualquiera puede venir null (si ambos null, no hace nada).
 * El clientId y el requestId NO viajan aqui (JWT + path).
 * TODO: revisar validaciones.
 */
@Schema(description = "Rating y/o resena que el cliente deja a un servicio")
public record SubmitServiceFeedbackForm(
        @Min(1) @Max(5) Integer rating,
        String comment,
        List<Long> tagIds) {
}
