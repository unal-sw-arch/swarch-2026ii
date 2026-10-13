package com.parosurvivors.serviya.profiles.infrastructure.dto.form;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Entrada web (Form) de actualizacion parcial del perfil de oferente. PATCH /api/v1/offerers/me (RF-012, RF-015).
 * Campos editables: whatsappNumber, publicDescription, specialty. El userId se extrae del JWT.
 * TODO: revisar validaciones.
 */
@Schema(description = "Campos editables del perfil de oferente (PATCH parcial)")
public record UpdateOffererProfileForm(
        String whatsappNumber,
        String publicDescription,
        String specialty) {
}
