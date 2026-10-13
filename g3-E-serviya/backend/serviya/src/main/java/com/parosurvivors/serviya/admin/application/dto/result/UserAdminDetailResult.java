package com.parosurvivors.serviya.admin.application.dto.result;

import com.parosurvivors.serviya.metrics.domain.ClientMetrics;
import com.parosurvivors.serviya.metrics.domain.OffererMetrics;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Salida de aplicacion (Result) del detalle de un usuario para el panel admin (CQRS-light, RF-081).
 * Vista agregada: cuenta + perfil + metricas precalculadas del modulo de metricas (oferente y cliente,
 * ceros si el usuario no tiene ese rol) + conteos de moderacion (reportes). Las metricas de comportamiento
 * NO se recalculan aqui: se leen del modulo de metricas (fuente unica). Los reportes no son metricas
 * (dominio de moderacion) y se cuentan en el modulo reports. Lo devuelve AdminService.getUserAdminDetail.
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
        OffererMetrics offererMetrics,
        ClientMetrics clientMetrics,
        // --- Moderacion (modulo reports) ---
        Integer reportsReceived,
        Integer reportsSent) {
}
