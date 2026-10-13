package com.parosurvivors.serviya.reports.infrastructure.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Salida web (Response) del enlace creado al reportar una solicitud. POST /api/v1/reports/requests.
 * Mapea desde el dominio RequestReport.
 */
@Schema(description = "Reporte de solicitud creado")
public record RequestReportResponse(
        @Schema(accessMode = Schema.AccessMode.READ_ONLY) Long id,
        Long reportId,
        Long requestId) {
}
