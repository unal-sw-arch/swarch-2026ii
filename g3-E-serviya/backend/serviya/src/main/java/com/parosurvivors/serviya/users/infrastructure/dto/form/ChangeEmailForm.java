package com.parosurvivors.serviya.users.infrastructure.dto.form;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * Entrada web (Form) para cambiar el correo propio. PATCH /api/v1/users/me/email (RF-007).
 * El userId se extrae del JWT en el controller.
 * TODO: revisar validaciones.
 */
@Schema(description = "Cambio de correo del usuario autenticado")
public record ChangeEmailForm(
        @NotBlank @Email String newEmail) {
}
