package com.parosurvivors.serviya.metrics.api.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Metricas agregadas de un cliente.
 */
public record ClientMetricsSnapshot(
        Long clientId,
        BigDecimal averageRating,
        int totalRatings,
        int totalComments,
        int totalPositiveTags,
        int totalNegativeTags,
        int totalRequestsSent,
        int totalAcceptedRequests,
        int totalCompletedRequests,
        int totalCancelledRequests,
        int totalRescheduledRequests,
        int totalNotProvidedRequests,
        LocalDateTime updatedAt) {
}
