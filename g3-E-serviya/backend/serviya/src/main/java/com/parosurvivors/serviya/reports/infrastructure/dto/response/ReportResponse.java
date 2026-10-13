package com.parosurvivors.serviya.reports.infrastructure.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/**
 * Salida web (Response) de un reporte (fila de listado). Mapea desde el dominio Report.
 * TODO: revisar campos.
 */
@Schema(description = "Reporte (vista de listado)")
public record ReportResponse(
        @Schema(accessMode = Schema.AccessMode.READ_ONLY) Long id,
        Long reporterId,
        Long reportedUserId,
        String reportType,
        String category,
        String reason,
        String status,
        String priority,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
}
