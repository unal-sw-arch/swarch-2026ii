package com.parosurvivors.serviya.users.infrastructure.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Salida web (Response) de un rol del usuario. GET /api/v1/users/me/roles.
 * TODO: revisar campos.
 */
@Schema(description = "Rol asignado a un usuario")
public record RoleResponse(
        @Schema(accessMode = Schema.AccessMode.READ_ONLY) Integer id,
        @Schema(description = "Nombre del rol", example = "CLIENT") String name) {
}
