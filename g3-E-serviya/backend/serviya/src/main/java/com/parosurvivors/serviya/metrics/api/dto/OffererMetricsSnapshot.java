package com.parosurvivors.serviya.metrics.api.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Metricas agregadas de un oferente.
 */
public record OffererMetricsSnapshot(
        Long offererId,
        BigDecimal averageRating,
        int totalRatings,
        int totalComments,
        int totalPositiveTags,
        int totalNegativeTags,
        int totalRequestsReceived,
        int totalAcceptedRequests,
        int totalCompletedServices,
        int totalCancelledServices,
        int totalRescheduleProposalsSent,
        int totalNotProvidedServices,
        LocalDateTime updatedAt) {
}
