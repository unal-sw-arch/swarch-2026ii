package com.parosurvivors.serviya.bff.application.dto;

/** Metricas propias del usuario autenticado (oferente y/o cliente). */
public record UserMetricsView(
        OffererMetricsView offererMetrics,
        ClientMetricsView clientMetrics) {
}
