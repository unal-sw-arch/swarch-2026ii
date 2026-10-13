package com.parosurvivors.serviya.users.infrastructure.dto.form;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * Entrada web (Form) para solicitar recuperacion de contrasena. POST /api/v1/auth/password-reset (RF-003).
 * TODO: revisar validaciones.
 */
@Schema(description = "Solicitud de recuperacion de contrasena")
public record RequestPasswordResetForm(
        @NotBlank @Email String email) {
}
