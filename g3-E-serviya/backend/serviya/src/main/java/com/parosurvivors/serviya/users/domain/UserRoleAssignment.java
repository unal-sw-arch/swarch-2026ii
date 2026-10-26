package com.parosurvivors.serviya.users.domain;

import java.time.LocalDateTime;

/**
 * Rol de un usuario ya hidratado con su nombre y su fecha de concesion (RF-067).
 * Es la vista que consume el panel de administracion: "que roles tiene y desde cuando".
 */
public record UserRoleAssignment(Integer roleId, RoleName name, LocalDateTime assignedAt) {
}
