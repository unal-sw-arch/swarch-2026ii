package com.parosurvivors.serviya.reports.api.dto;

/**
 * Resumen de un reporte con el id del objetivo segun su tipo.
 */
public record ReportSummarySnapshot(
        Long id,
        Long reporterId,
        Long reportedUserId,
        String reportType,
        String category,
        String status,
        Long requestId,
        Long serviceFeedbackId,
        Long clientFeedbackId) {
}
