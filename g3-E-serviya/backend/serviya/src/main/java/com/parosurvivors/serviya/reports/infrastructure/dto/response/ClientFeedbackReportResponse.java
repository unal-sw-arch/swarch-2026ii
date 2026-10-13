package com.parosurvivors.serviya.reports.infrastructure.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Salida web (Response) del enlace creado al reportar una resena de cliente.
 * POST /api/v1/reports/client-feedback. Mapea desde el dominio ClientFeedbackReport.
 */
@Schema(description = "Reporte de resena de cliente creado")
public record ClientFeedbackReportResponse(
        @Schema(accessMode = Schema.AccessMode.READ_ONLY) Long id,
        Long reportId,
        Long feedbackId) {
}
