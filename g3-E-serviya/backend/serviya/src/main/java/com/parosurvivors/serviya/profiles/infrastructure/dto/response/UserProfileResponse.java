package com.parosurvivors.serviya.profiles.infrastructure.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Salida web (Response) del perfil personal del usuario autenticado. GET /api/v1/users/me/profile (RF-005).
 * Los campos cifrados (documentNumber, phoneNumber) ya vienen descifrados desde el dominio.
 * TODO: revisar campos.
 */
@Schema(description = "Informacion personal del usuario")
public record UserProfileResponse(
        @Schema(accessMode = Schema.AccessMode.READ_ONLY) Long id,
        Long userId,
        String fullName,
        String documentType,
        String documentNumber,
        String phoneNumber,
        Long primaryAddressId,
        String profilePhotoUrl,
        String bio,
        String profileType) {
}
