package com.parosurvivors.serviya.metrics.api.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Metricas agregadas de un servicio.
 */
public record ServiceMetricsSnapshot(
        Long serviceId,
        BigDecimal averageRating,
        int totalRatings,
        int totalComments,
        int totalRequestsReceived,
        LocalDateTime updatedAt) {
}
