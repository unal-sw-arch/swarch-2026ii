package com.parosurvivors.serviya.users.infrastructure.dto.form;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * Entrada web (Form) de inicio de sesion. POST /api/v1/auth/login (RF-001).
 * TODO: revisar validaciones.
 */
@Schema(description = "Credenciales de inicio de sesion")
public record LoginForm(
        @NotBlank @Email String email,
        @NotBlank String password) {
}
