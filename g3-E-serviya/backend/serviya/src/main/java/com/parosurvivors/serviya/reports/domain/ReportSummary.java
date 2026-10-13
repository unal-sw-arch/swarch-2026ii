package com.parosurvivors.serviya.reports.domain;

/**
 * Read-model reducido de un reporte para orquestación interna (moderación): los campos base del reporte
 * + el id de la entidad objetivo según el tipo (solo el correspondiente al reportType viene no-nulo).
 * NO enriquece con perfiles ni con el detalle de la solicitud/feedback (eso lo hace getReportDetail, que
 * es para el endpoint de display). Se usa cuando solo se necesita el reporte y su objetivo, no las
 * entidades relacionadas.
 */
public record ReportSummary(
        Long id,
        Long reporterId,
        Long reportedUserId,
        ReportType reportType,
        String category,
        ReportStatus status,
        Long requestId,
        Long serviceFeedbackId,
        Long clientFeedbackId) {
}
