package com.parosurvivors.serviya.metrics.infrastructure.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Salida web (Response) de todas las metricas de un cliente. GET /api/v1/clients/{id}/metrics (RF-054).
 * Mapea desde el dominio ClientMetrics.
 */
@Schema(description = "Metricas precalculadas de un cliente")
public record ClientMetricsResponse(
        Long clientId,
        BigDecimal averageRating,
        Integer totalRatings,
        Integer totalComments,
        Integer totalPositiveTags,
        Integer totalNegativeTags,
        Integer totalRequestsSent,
        Integer totalAcceptedRequests,
        Integer totalCompletedRequests,
        Integer totalCancelledRequests,
        Integer totalRescheduledRequests,
        Integer totalNotProvidedRequests,
        Integer activeRequests,
        LocalDateTime updatedAt) {
}
