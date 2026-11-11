package com.parosurvivors.serviya.bff.infrastructure.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDateTime;

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
