package com.parosurvivors.serviya.metrics.infrastructure.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

/**
 * Salida web (Response) del resumen de metricas de un oferente. GET /api/v1/offerers/{id}/metrics/main
 * (RF-042, RF-053). Subconjunto de OffererMetrics (mapea desde el dominio).
 * TODO: revisar que campos componen el "resumen".
 */
@Schema(description = "Resumen de metricas de un oferente")
public record OffererMetricsSummaryResponse(
        Long offererId,
        BigDecimal averageRating,
        Integer totalRatings,
        Integer totalCompletedServices,
        Integer totalCancelledServices) {
}
