package com.parosurvivors.serviya.bff.application.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Metricas completas de un oferente. */
public record OffererMetricsView(
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
