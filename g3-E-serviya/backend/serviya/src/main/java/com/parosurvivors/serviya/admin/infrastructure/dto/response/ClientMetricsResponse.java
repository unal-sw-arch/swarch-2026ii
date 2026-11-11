package com.parosurvivors.serviya.admin.infrastructure.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Metricas de cliente en el detalle admin. Copia estable del contrato web de metrics.
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
