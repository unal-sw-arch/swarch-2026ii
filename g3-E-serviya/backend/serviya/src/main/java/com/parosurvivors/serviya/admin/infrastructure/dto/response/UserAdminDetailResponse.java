package com.parosurvivors.serviya.admin.infrastructure.dto.response;

import com.parosurvivors.serviya.metrics.infrastructure.dto.response.ClientMetricsResponse;
import com.parosurvivors.serviya.metrics.infrastructure.dto.response.OffererMetricsResponse;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Salida web (Response) del detalle de un usuario para el panel admin. GET /api/v1/admin/users/{id} (RF-081).
 * Mapea desde UserAdminDetailResult. Las metricas reutilizan las Response del modulo de metricas.
 */
@Schema(description = "Detalle de un usuario (vista admin): cuenta + perfil + metricas + moderacion")
public record UserAdminDetailResponse(
        // --- Cuenta ---
        @Schema(accessMode = Schema.AccessMode.READ_ONLY) Long id,
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
        OffererMetricsResponse offererMetrics,
        ClientMetricsResponse clientMetrics,
        // --- Moderacion (modulo reports) ---
        Integer reportsReceived,
        Integer reportsSent) {
}
