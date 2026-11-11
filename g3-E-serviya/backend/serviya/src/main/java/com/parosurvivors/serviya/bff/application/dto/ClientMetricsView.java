package com.parosurvivors.serviya.bff.application.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Metricas completas de un cliente, con el conteo de solicitudes activas compuesto por el BFF. */
public record ClientMetricsView(
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
