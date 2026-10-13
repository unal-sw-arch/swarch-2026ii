package com.parosurvivors.serviya.users.infrastructure.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Datos públicos de perfil de usuario. Devueltos al obtener información de un usuario específico.
 */
@Schema(description = "Información pública del perfil de usuario")
public record UserProfileResponse(
        @Schema(description = "ID del usuario") Long id,
        @Schema(description = "Nombre completo del usuario") String fullName,
        @Schema(description = "Email del usuario") String email,
        @Schema(description = "URL de foto de perfil del usuario") String profilePhotoUrl,
        @Schema(description = "Número de teléfono del usuario") String phoneNumber,
        @Schema(description = "Descripción/biografía del usuario") String bio) {
}
