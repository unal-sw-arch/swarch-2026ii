package com.parosurvivors.serviya.users.infrastructure.dto.form;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Entrada web (Form) para confirmar la recuperacion de contrasena con el token. RF-003.
 * POST /api/v1/auth/password-reset/confirm. El token viaja en el body (no en query) por ser sensible.
 * TODO: revisar validaciones.
 */
@Schema(description = "Confirmacion de recuperacion de contrasena con token")
public record ConfirmPasswordResetForm(
        @NotBlank String token,
        @NotBlank @Size(min = 8) String newPassword) {
}
