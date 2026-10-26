package com.parosurvivors.serviya.metrics.infrastructure.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

/**
 * Salida web (Response) del resumen de metricas de un cliente. GET /api/v1/clients/{id}/metrics/main (RF-054).
 * Subconjunto de ClientMetrics (mapea desde el dominio).
 * TODO: revisar que campos componen el "resumen".
 */
@Schema(description = "Resumen de metricas de un cliente")
public record ClientMetricsSummaryResponse(
        Long clientId,
        BigDecimal averageRating,
        Integer totalRatings,
        Integer totalCompletedRequests,
        Integer totalCancelledRequests) {
}
