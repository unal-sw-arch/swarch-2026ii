package com.parosurvivors.serviya.admin.infrastructure.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/**
 * Salida web (Response) de un usuario en el panel admin (fila de listado y resultado de creacion).
 * En el listado mapea desde UserSummaryItem (con nombre y foto del perfil); en la creacion mapea desde
 * el dominio User + el nombre del formulario (sin foto aun). GET /api/v1/admin/users, POST /api/v1/admin/users.
 */
@Schema(description = "Usuario (vista de listado admin)")
public record UserSummaryResponse(
        @Schema(accessMode = Schema.AccessMode.READ_ONLY) Long id,
        String email,
        String fullName,
        String photoUrl,
        Boolean banned,
        LocalDateTime deletedAt,
        LocalDateTime createdAt) {
}
