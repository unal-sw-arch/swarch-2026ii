package com.parosurvivors.serviya.users.application.ports.output;

import com.parosurvivors.serviya.users.domain.RoleAssignment;
import java.util.List;

/**
 * Puerto de salida para la tabla puente {@code user_roles}. Trabaja con identificadores
 * planos (no hay objeto de dominio para la asignación); la hidratación de roles de un
 * usuario se compone en el servicio de aplicación junto con {@link RolePersistencePort}.
 */
public interface UserRolePersistencePort {
    void assignRole(Long userId, Integer roleId);
    void removeRole(Long userId, Integer roleId);
    boolean existsByUserIdAndRoleId(Long userId, Integer roleId);
    List<Integer> findRoleIdsByUserId(Long userId);
    /** Roles del usuario CON su fecha de concesion (RF-067). */
    List<RoleAssignment> findAssignmentsByUserId(Long userId);
    /** Ids de los usuarios que tienen un rol dado (p. ej. todos los ADMIN, para notificarles). */
    List<Long> findUserIdsByRoleId(Integer roleId);
}
