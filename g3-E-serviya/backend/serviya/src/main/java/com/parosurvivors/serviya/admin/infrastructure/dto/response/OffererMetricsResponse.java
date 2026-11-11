package com.parosurvivors.serviya.admin.infrastructure.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Metricas de oferente en el detalle admin. Copia estable del contrato web de metrics.
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
