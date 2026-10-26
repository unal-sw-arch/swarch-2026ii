package com.parosurvivors.serviya.metrics.infrastructure.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Salida web (Response) del conteo de un tag para un servicio. GET /api/v1/services/{id}/tag-metrics.
 * Mapea desde el dominio ServiceTagMetrics.
 */
@Schema(description = "Conteo de un tag de resena para un servicio")
public record ServiceTagMetricsResponse(
        Long tagId,
        Long serviceId,
        Integer tagCount) {
}
