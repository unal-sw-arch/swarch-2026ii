package com.parosurvivors.serviya.admin.infrastructure.dto.form;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/**
 * Entrada web (Form) para conceder un rol a un usuario desde el panel admin.
 * POST /api/v1/admin/users/{id}/roles (RF-065). El userId va en el path y el adminId se extrae del JWT.
 * Cubre cualquier rol (CLIENT/OFFERER/ADMIN) identificado por su nombre.
 */
@Schema(description = "Rol a conceder a un usuario (por nombre)")
public record GrantRoleForm(
        @NotBlank String role) {
}
