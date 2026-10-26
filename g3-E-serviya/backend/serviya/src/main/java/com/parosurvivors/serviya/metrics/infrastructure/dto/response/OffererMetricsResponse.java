package com.parosurvivors.serviya.metrics.infrastructure.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Salida web (Response) de todas las metricas de un oferente. GET /api/v1/offerers/{id}/metrics (RF-042, RF-053).
 * Mapea desde el dominio OffererMetrics.
 */
@Schema(description = "Metricas precalculadas de un oferente")
public record OffererMetricsResponse(
        Long offererId,
        BigDecimal averageRating,
        Integer totalRatings,
        Integer totalComments,
        Integer totalPositiveTags,
        Integer totalNegativeTags,
        Integer totalRequestsReceived,
        Integer totalAcceptedRequests,
        Integer totalCompletedServices,
        Integer totalCancelledServices,
        Integer totalRescheduleProposalsSent,
        Integer totalNotProvidedServices,
        LocalDateTime updatedAt) {
}
