package com.parosurvivors.serviya.users.application.ports.input;

import com.parosurvivors.serviya.users.domain.Role;
import com.parosurvivors.serviya.users.domain.RoleName;
import com.parosurvivors.serviya.users.domain.UserRoleAssignment;

import java.util.List;

/**
 * Puerto de entrada de UserRoleService.
 * Ver documents/project-structure/estructura-servicios.docx (módulo 1).
 */
public interface UserRoleServicePort {

    List<Role> getUserRoles(Long userId);

    boolean hasRole(Long userId, String roleName);

    /** Asigna un rol por nombre. Punto unico de validacion de existencia del rol y de duplicado. */
    void assignRole(Long userId, RoleName roleName);

    void removeRole(Long userId, Long roleId);

    /** RF-066: quita un rol identificado por nombre. La cascada asociada la orquesta AdminService. */
    void revokeRole(Long userId, RoleName roleName);

    /** RF-067: roles del usuario con la fecha en que se concedio cada uno. */
    List<UserRoleAssignment> getUserRoleAssignments(Long userId);

    /**
     * Ids de los usuarios que tienen un rol dado. Lo usa el modulo de reportes para notificar a la
     * cola de administradores (RF-073) sin acoplarse a la persistencia de usuarios.
     */
    List<Long> findUserIdsByRole(RoleName roleName);

    void acquireRole(Long userId, String roleName);
}
