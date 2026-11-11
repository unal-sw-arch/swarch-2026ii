package com.parosurvivors.serviya.bff.infrastructure.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;

/**
 * Resumen publico de un oferente. Misma forma JSON que el endpoint historico de profiles.
 */
@Schema(description = "Resumen del perfil de un oferente")
public record OffererProfileSummaryResponse(
        @Schema(accessMode = Schema.AccessMode.READ_ONLY) Long userId,
        String fullName,
        String profilePhotoUrl,
        String specialty,
        BigDecimal averageRating) {
}
