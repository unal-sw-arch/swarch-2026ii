package com.parosurvivors.serviya.profiles.infrastructure.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Salida web (Response) del perfil publico del oferente. GET /api/v1/offerers/{id} (RF-015, RF-027);
 * tambien devuelto tras PATCH /api/v1/offerers/me.
 * TODO: revisar campos (p. ej. metricas principales agregadas).
 */
@Schema(description = "Perfil publico de un oferente")
public record OffererPublicProfileResponse(
        @Schema(accessMode = Schema.AccessMode.READ_ONLY) Long userId,
        String whatsappNumber,
        String publicDescription,
        String specialty) {
}
