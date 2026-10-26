package com.parosurvivors.serviya.metrics.infrastructure.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Salida web (Response) de las metricas propias segun los roles del usuario. GET /api/v1/users/me/metrics
 * (RF-051, RF-052). Cualquiera de los dos puede venir null si el usuario no tiene ese rol.
 */
@Schema(description = "Metricas del usuario autenticado segun sus roles")
public record UserMetricsResponse(
        OffererMetricsResponse offererMetrics,
        ClientMetricsResponse clientMetrics) {
}
