package com.parosurvivors.serviya.admin.infrastructure.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

/**
 * Salida web (Response) de un rol asignado a un usuario, con su fecha de concesion.
 * GET /api/v1/admin/users/{id}/roles (RF-067).
 */
@Schema(description = "Rol asignado a un usuario y desde cuando lo tiene")
public record UserRoleAssignmentResponse(
        @Schema(accessMode = Schema.AccessMode.READ_ONLY) Integer roleId,
        @Schema(description = "Nombre del rol", example = "OFFERER") String name,
        @Schema(description = "Momento en que se concedio el rol") LocalDateTime assignedAt) {
}
