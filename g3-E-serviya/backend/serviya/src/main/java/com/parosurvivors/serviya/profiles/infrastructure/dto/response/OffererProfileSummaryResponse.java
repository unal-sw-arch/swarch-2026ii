package com.parosurvivors.serviya.profiles.infrastructure.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

/**
 * Salida web (Response) del resumen del perfil de oferente. GET /api/v1/offerers/{id}/summary.
 * Mapea desde el read model de dominio OffererProfileSummary.
 * TODO: revisar campos.
 */
@Schema(description = "Resumen del perfil de un oferente")
public record OffererProfileSummaryResponse(
        @Schema(accessMode = Schema.AccessMode.READ_ONLY) Long userId,
        String fullName,
        String profilePhotoUrl,
        String specialty,
        BigDecimal averageRating) {
}
