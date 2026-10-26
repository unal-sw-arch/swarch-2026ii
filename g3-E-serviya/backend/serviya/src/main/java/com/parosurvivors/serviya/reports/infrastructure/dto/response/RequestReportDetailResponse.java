package com.parosurvivors.serviya.reports.infrastructure.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Salida web: payload del subtipo REQUEST del detalle de reporte. Mapea desde RequestReportDetail. */
@Schema(description = "Datos de la solicitud reportada (subtipo REQUEST)")
public record RequestReportDetailResponse(
        Long requestId,
        String serviceTitle,
        LocalDateTime scheduledDate,
        String status,
        BigDecimal requestedPrice,
        String city) {
}
