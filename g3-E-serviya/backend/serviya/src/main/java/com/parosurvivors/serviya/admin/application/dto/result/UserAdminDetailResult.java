package com.parosurvivors.serviya.admin.application.dto.result;

import com.parosurvivors.serviya.metrics.api.dto.ClientMetricsSnapshot;
import com.parosurvivors.serviya.metrics.api.dto.OffererMetricsSnapshot;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Salida de aplicacion (Result) del detalle de un usuario para el panel admin (CQRS-light, RF-081).
 * Vista agregada: cuenta + perfil + metricas precalculadas del modulo de metricas (oferente y cliente,
 * ceros si el usuario no tiene ese rol) + conteos de moderacion (reportes). Las metricas de comportamiento
 * NO se recalculan aqui: se leen de metrics.api. Los reportes no son metricas (dominio de moderacion) y
 * se cuentan en reports.api. Lo devuelve AdminService.getUserAdminDetail.
 */
public record UserAdminDetailResult(
        // --- Cuenta ---
        Long id,
        String email,
        Boolean banned,
        LocalDateTime deletedAt,
        LocalDateTime createdAt,
        List<String> roles,
        // --- Perfil ---
        String fullName,
        String profilePhotoUrl,
        String documentType,
        String documentNumber,
        String phoneNumber,
        String bio,
        String profileType,
        // --- Metricas precalculadas (modulo 6) ---
        OffererMetricsSnapshot offererMetrics,
        ClientMetricsSnapshot clientMetrics,
        Integer activeRequests,
        // --- Moderacion (modulo reports) ---
        Integer reportsReceived,
        Integer reportsSent) {
}
