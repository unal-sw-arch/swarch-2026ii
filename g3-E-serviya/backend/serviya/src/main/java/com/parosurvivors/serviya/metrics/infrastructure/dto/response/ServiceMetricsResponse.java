package com.parosurvivors.serviya.metrics.infrastructure.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Salida web (Response) de las metricas de un servicio. GET /api/v1/services/{id}/metrics (RF-040).
 * Mapea desde el dominio ServiceMetrics.
 */
@Schema(description = "Metricas precalculadas de un servicio")
public record ServiceMetricsResponse(
        Long serviceId,
        BigDecimal averageRating,
        Integer totalRatings,
        Integer totalComments,
        Integer totalRequestsReceived,
        LocalDateTime updatedAt) {
}
