package com.parosurvivors.serviya.admin.infrastructure.dto.form;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Entrada web (Form) para crear un usuario desde el panel admin. POST /api/v1/admin/users.
 * El adminId se extrae del JWT. El rol va en el body y puede ser cualquiera, incluido ADMIN (crear un ADMIN
 * de cuenta nueva); la promocion de una cuenta existente a ADMIN va por grantRoleByAdmin.
 */
@Schema(description = "Datos para crear un usuario (admin)")
public record CreateUserByAdminForm(
        @NotBlank @Email String email,
        @NotBlank @Size(min = 8) String password,
        @NotBlank String fullName,
        @NotBlank String role,
        String documentType,
        String documentNumber,
        String phone) {
}
