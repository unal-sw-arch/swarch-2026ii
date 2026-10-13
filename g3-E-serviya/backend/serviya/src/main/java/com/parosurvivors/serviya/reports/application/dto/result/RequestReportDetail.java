package com.parosurvivors.serviya.reports.application.dto.result;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Payload del subtipo REQUEST en el detalle de reporte: datos básicos de la solicitud reportada
 * (servicio, fecha programada, estado, precio, ciudad). Compuesto desde el detalle admin de la
 * solicitud. Sus campos pueden venir null si la solicitud ya no existe.
 */
public record RequestReportDetail(
        Long requestId,
        String serviceTitle,
        LocalDateTime scheduledDate,
        String status,
        BigDecimal requestedPrice,
        String city) {
}
