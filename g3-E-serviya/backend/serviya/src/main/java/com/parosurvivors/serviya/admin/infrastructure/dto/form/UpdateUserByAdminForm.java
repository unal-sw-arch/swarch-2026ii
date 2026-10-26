package com.parosurvivors.serviya.admin.infrastructure.dto.form;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;

/**
 * Entrada web (Form) de edicion de un usuario por el administrador.
 * PATCH /api/v1/admin/users/{id} (RF-068). Solo los campos no-nulos se actualizan.
 *
 * <p>El documento (tipo/numero) NO aparece: es inmutable en todo el sistema, tampoco lo cambia un admin.</p>
 */
@Schema(description = "Campos editables de un usuario desde el panel admin (PATCH parcial)")
public record UpdateUserByAdminForm(
        @Email String email,
        String fullName,
        String phone,
        String photoUrl,
        String description) {
}
