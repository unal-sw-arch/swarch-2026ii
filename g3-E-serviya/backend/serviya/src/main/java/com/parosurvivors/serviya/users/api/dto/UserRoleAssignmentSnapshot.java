package com.parosurvivors.serviya.users.api.dto;

import java.time.LocalDateTime;

/**
 * Asignacion de un rol a un usuario, tal como la expone identity.
 */
public record UserRoleAssignmentSnapshot(
        Integer roleId,
        String roleName,
        LocalDateTime assignedAt) {
}
