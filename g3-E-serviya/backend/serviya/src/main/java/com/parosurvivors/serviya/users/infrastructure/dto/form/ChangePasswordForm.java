package com.parosurvivors.serviya.users.infrastructure.dto.form;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Entrada web (Form) para cambiar la contrasena propia. PATCH /api/v1/users/me/password (RF-007).
 * El userId NO viaja aqui: se extrae del JWT en el controller.
 * TODO: revisar validaciones.
 */
@Schema(description = "Cambio de contrasena del usuario autenticado")
public record ChangePasswordForm(
        @NotBlank String currentPassword,
        @NotBlank @Size(min = 8) String newPassword) {
}
