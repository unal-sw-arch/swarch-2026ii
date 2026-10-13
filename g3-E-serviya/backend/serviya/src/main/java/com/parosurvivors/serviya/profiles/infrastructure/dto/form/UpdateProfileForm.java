package com.parosurvivors.serviya.profiles.infrastructure.dto.form;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Entrada web (Form) de actualizacion parcial del perfil personal. PATCH /api/v1/users/me/profile (RF-006).
 * Solo los campos no-nulos se actualizan (PATCH semantico). documentType/documentNumber NO son editables.
 * El userId se extrae del JWT en el controller.
 * TODO: revisar validaciones.
 */
@Schema(description = "Campos editables del perfil personal (PATCH parcial)")
public record UpdateProfileForm(
        String fullName,
        String phone,
        String photoUrl,
        String description) {
}
