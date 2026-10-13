package com.parosurvivors.serviya.reports.application.dto.result;

import java.time.LocalDateTime;

/**
 * Salida de aplicacion (Result) del detalle enriquecido de un reporte (CQRS-light), para el panel de
 * moderación. Trae los campos base del Report + el resumen de ambas partes (reportante/reportado) +
 * el payload del subtipo correspondiente al reportType (solo uno no-nulo: {@code request} para REQUEST,
 * {@code feedback} para SERVICE_FEEDBACK/CLIENT_FEEDBACK). Lo compone ReportService.getReportDetail.
 */
public record ReportDetailResult(
        Long id,
        Long reporterId,
        Long reportedUserId,
        String reportType,
        String category,
        String reason,
        String status,
        String priority,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        // Partes del reporte (nombre + foto), siempre presentes.
        PartySummary reporter,
        PartySummary reported,
        // Payload por subtipo: solo el correspondiente al reportType viene no-nulo.
        RequestReportDetail request,
        FeedbackReportDetail feedback) {
}
