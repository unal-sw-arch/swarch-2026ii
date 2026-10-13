package com.parosurvivors.serviya.metrics.infrastructure.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Salida web (Response) del conteo de un tag para un cliente. GET /api/v1/clients/{id}/tag-metrics.
 * Mapea desde el dominio ClientTagMetrics.
 */
@Schema(description = "Conteo de un tag de resena para un cliente")
public record ClientTagMetricsResponse(
        Long clientId,
        Long tagId,
        String tagName,
        boolean positive,
        Integer tagCount) {
}
