package com.parosurvivors.serviya.bff.infrastructure.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Metricas del usuario autenticado")
public record UserMetricsResponse(
        OffererMetricsResponse offererMetrics,
        ClientMetricsResponse clientMetrics) {
}
