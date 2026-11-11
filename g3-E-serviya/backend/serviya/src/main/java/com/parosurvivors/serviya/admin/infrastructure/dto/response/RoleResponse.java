package com.parosurvivors.serviya.admin.infrastructure.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Salida web (Response) de un rol del sistema para el panel admin.
 */
@Schema(description = "Rol del sistema")
public record RoleResponse(
        @Schema(accessMode = Schema.AccessMode.READ_ONLY) Integer id,
        @Schema(description = "Nombre del rol", example = "CLIENT") String name) {
}
