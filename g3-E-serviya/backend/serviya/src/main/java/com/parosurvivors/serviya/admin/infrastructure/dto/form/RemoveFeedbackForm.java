package com.parosurvivors.serviya.admin.infrastructure.dto.form;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Entrada web (Form) para eliminar feedback inapropiado no reportado (admin). POST /api/v1/admin/feedback/remove
 * (RF-049). Crea internamente un reporte (admin como reportante) y lo resuelve via revertFeedbackFromReport.
 * El adminId se extrae del JWT.
 * TODO: revisar campos exactos (que identifican la resena a eliminar) y validaciones.
 */
@Schema(description = "Datos para eliminar directamente una resena inapropiada")
public record RemoveFeedbackForm(
        @Schema(description = "Tipo de feedback objetivo. Nombre exacto del tipo: SERVICE_FEEDBACK o CLIENT_FEEDBACK.",
                allowableValues = {"SERVICE_FEEDBACK", "CLIENT_FEEDBACK"}, example = "SERVICE_FEEDBACK")
        @NotBlank String targetType,
        @Schema(description = "Id del feedback (service_feedback.id o client_feedback.id) a eliminar")
        @NotNull Long targetId,
        @NotNull Long reportedUserId,
        @NotBlank String category,
        String reason) {
}
