package com.parosurvivors.serviya.reports.infrastructure.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/**
 * Salida web (Response) del detalle enriquecido de un reporte. Mapea desde ReportDetailResult.
 * Incluye el resumen de ambas partes y el payload del subtipo (solo el correspondiente al reportType
 * viene no-nulo: {@code request} para REQUEST, {@code feedback} para SERVICE_FEEDBACK/CLIENT_FEEDBACK).
 */
@Schema(description = "Detalle enriquecido de un reporte (partes + payload del subtipo)")
public record ReportDetailResponse(
        @Schema(accessMode = Schema.AccessMode.READ_ONLY) Long id,
        Long reporterId,
        Long reportedUserId,
        String reportType,
        String category,
        String reason,
        String status,
        String priority,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        PartySummaryResponse reporter,
        PartySummaryResponse reported,
        RequestReportDetailResponse request,
        FeedbackReportDetailResponse feedback) {
}
