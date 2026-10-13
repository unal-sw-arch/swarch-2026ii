package com.parosurvivors.serviya.reports.infrastructure.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/**
 * Salida web (Response) de una accion de moderacion sobre un reporte. GET /api/v1/reports/{id}/actions (RF-071).
 * Mapea desde el dominio ReportAction.
 */
@Schema(description = "Accion de moderacion registrada sobre un reporte")
public record ReportActionResponse(
        @Schema(accessMode = Schema.AccessMode.READ_ONLY) Long id,
        Long reportId,
        Long adminId,
        String actionDescription,
        LocalDateTime createdAt) {
}
