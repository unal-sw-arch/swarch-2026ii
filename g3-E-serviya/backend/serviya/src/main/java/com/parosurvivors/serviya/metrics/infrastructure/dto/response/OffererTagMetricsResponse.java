package com.parosurvivors.serviya.metrics.infrastructure.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Salida web (Response) del conteo de un tag para un oferente. GET /api/v1/offerers/{id}/tag-metrics.
 * Mapea desde el dominio OffererTagMetrics.
 */
@Schema(description = "Conteo de un tag de resena para un oferente")
public record OffererTagMetricsResponse(
        Long offererId,
        Long tagId,
        String tagName,
        boolean positive,
        Integer tagCount) {
}
