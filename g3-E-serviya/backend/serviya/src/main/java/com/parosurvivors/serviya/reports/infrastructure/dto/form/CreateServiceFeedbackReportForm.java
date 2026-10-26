package com.parosurvivors.serviya.reports.infrastructure.dto.form;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Entrada web (Form) para reportar una resena de servicio. POST /api/v1/reports/service-feedback (RF-056).
 * El reporterId se extrae del JWT.
 * TODO: revisar validaciones.
 */
@Schema(description = "Datos para reportar una resena de servicio")
public record CreateServiceFeedbackReportForm(
        @NotNull Long reportedUserId,
        @NotBlank String category,
        String customCategory,
        String reason,
        @NotNull Long serviceFeedbackId) {
}
