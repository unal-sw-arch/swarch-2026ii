package com.parosurvivors.serviya.feedback.infrastructure.dto.form;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * Entrada web (Form) del feedback de un oferente a un cliente. POST /api/v1/service-requests/{id}/client-feedback
 * (RF-043, RF-044). El offererId y el requestId NO viajan aqui (JWT + path); el clientId calificado si.
 * TODO: revisar validaciones (posiblemente derivar clientId de la solicitud).
 */
@Schema(description = "Rating y/o resena que el oferente deja a un cliente")
public record SubmitClientFeedbackForm(
        @NotNull Long clientId,
        @Min(1) @Max(5) Integer rating,
        String comment,
        List<Long> tagIds) {
}
