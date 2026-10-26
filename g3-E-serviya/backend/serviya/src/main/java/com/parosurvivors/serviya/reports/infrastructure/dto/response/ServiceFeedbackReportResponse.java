package com.parosurvivors.serviya.reports.infrastructure.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Salida web (Response) del enlace creado al reportar una resena de servicio.
 * POST /api/v1/reports/service-feedback. Mapea desde el dominio ServiceFeedbackReport.
 */
@Schema(description = "Reporte de resena de servicio creado")
public record ServiceFeedbackReportResponse(
        @Schema(accessMode = Schema.AccessMode.READ_ONLY) Long id,
        Long reportId,
        Long feedbackId) {
}
