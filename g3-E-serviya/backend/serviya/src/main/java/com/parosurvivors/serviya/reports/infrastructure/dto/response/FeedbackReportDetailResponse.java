package com.parosurvivors.serviya.reports.infrastructure.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;

/** Salida web: payload de los subtipos de feedback del detalle de reporte. Mapea desde FeedbackReportDetail. */
@Schema(description = "Contenido del feedback reportado (subtipos SERVICE_FEEDBACK / CLIENT_FEEDBACK)")
public record FeedbackReportDetailResponse(
        Long feedbackId,
        @Schema(description = "SERVICE o CLIENT") String kind,
        Integer rating,
        String comment,
        List<String> tags,
        LocalDateTime createdAt) {
}
