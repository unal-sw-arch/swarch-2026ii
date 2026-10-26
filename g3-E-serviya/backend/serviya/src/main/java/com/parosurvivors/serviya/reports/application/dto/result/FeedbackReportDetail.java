package com.parosurvivors.serviya.reports.application.dto.result;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Payload de los subtipos de feedback (SERVICE_FEEDBACK / CLIENT_FEEDBACK) en el detalle de reporte:
 * el contenido del feedback reportado (rating + comentario + etiquetas). {@code kind} distingue
 * "SERVICE" vs "CLIENT". Si el feedback ya fue revertido (el revert borra la fila), solo vienen
 * {@code feedbackId} y {@code kind}; el resto queda null/vacío.
 */
public record FeedbackReportDetail(
        Long feedbackId,
        String kind,
        Integer rating,
        String comment,
        List<String> tags,
        LocalDateTime createdAt) {
}
