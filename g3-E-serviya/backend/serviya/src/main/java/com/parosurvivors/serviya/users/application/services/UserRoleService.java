package com.parosurvivors.serviya.users.application.services;

import com.parosurvivors.serviya.shared.exceptions.InvalidStateException;
import com.parosurvivors.serviya.shared.exceptions.ResourceNotFoundException;
import com.parosurvivors.serviya.users.application.ports.input.UserRoleServicePort;
import com.parosurvivors.serviya.shared.events.application.ports.output.DomainEventPublisherPort;
import com.parosurvivors.serviya.shared.events.domain.RoleAssignedEvent;
import com.parosurvivors.serviya.users.application.ports.output.RolePersistencePort;
import com.parosurvivors.serviya.users.application.ports.output.UserRolePersistencePort;
import com.parosurvivors.serviya.users.domain.Role;
import com.parosurvivors.serviya.users.domain.RoleName;
import com.parosurvivors.serviya.users.domain.UserRoleAssignment;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Gestion de roles de un usuario sobre la tabla puente user_roles. {@code getUserRoles} hidrata
 * los roles (usado por login y por GET /users/me/roles) y {@code acquireRole} asigna un rol
 * publico (CLIENT u OFFERER, nunca ADMIN), usado por el registro y por la auto-asignacion.
 * Ver documents/project-structure/estructura-servicios.docx.
 */
@Component
@RequiredArgsConstructor
public class UserRoleService implements UserRoleServicePort {

    private final UserRolePersistencePort userRolePersistencePort;
    private final RolePersistencePort rolePersistencePort;
    /** Publica RoleAssignedEvent: metrics (y profiles) inicializan sus filas 1-a-1 sin acoplarse aqui. */
    private final DomainEventPublisherPort domainEventPublisher;

    @Override
    public List<Role> getUserRoles(Long userId) {
        return userRolePersistencePort.findRoleIdsByUserId(userId).stream()
                .map(roleId -> rolePersistencePort.findById(roleId)
                        .orElseThrow(() -> new ResourceNotFoundException("Role not found with id: " + roleId)))
                .toList();
    }

    @Override
    public boolean hasRole(Long userId, String roleName) {
        RoleName target = parseRole(roleName);
        return getUserRoles(userId).stream().anyMatch(role -> role.getName() == target);
    }

    @Override
    @Transactional
    public void assignRole(Long userId, RoleName roleName) {
        // Punto unico de validacion de existencia del rol (por nombre) + duplicado + persistencia.
        Role role = rolePersistencePort.findByName(roleName)
                .orElseThrow(() -> new ResourceNotFoundException("Role not found: " + roleName));
        doAssign(userId, role.getId(), roleName);
    }

    @Override
    public void removeRole(Long userId, Long roleId) {
        userRolePersistencePort.removeRole(userId, toRoleId(roleId));
    }

    /**
     * RF-066: quita el rol identificandolo por NOMBRE (el admin razona en CLIENT/OFFERER/ADMIN, no en ids).
     * Solo retira la fila de user_roles; la cascada sobre servicios y solicitudes la orquesta AdminService,
     * que es quien conoce esa regla de negocio.
     */
    @Override
    public void revokeRole(Long userId, RoleName roleName) {
        Role role = rolePersistencePort.findByName(roleName)
                .orElseThrow(() -> new ResourceNotFoundException("Role not found: " + roleName));
        if (!userRolePersistencePort.existsByUserIdAndRoleId(userId, role.getId())) {
            throw new InvalidStateException("User " + userId + " does not have role: " + roleName);
        }
        userRolePersistencePort.removeRole(userId, role.getId());
    }

    /** RF-067: roles del usuario con la fecha en que se le concedio cada uno. */
    @Override
    public List<UserRoleAssignment> getUserRoleAssignments(Long userId) {
        return userRolePersistencePort.findAssignmentsByUserId(userId).stream()
                .map(assignment -> {
                    Role role = rolePersistencePort.findById(assignment.roleId())
                            .orElseThrow(() -> new ResourceNotFoundException(
                                    "Role not found with id: " + assignment.roleId()));
                    return new UserRoleAssignment(role.getId(), role.getName(), assignment.assignedAt());
                })
                .toList();
    }

    @Override
    public List<Long> findUserIdsByRole(RoleName roleName) {
        Role role = rolePersistencePort.findByName(roleName)
                .orElseThrow(() -> new ResourceNotFoundException("Role not found: " + roleName));
        return userRolePersistencePort.findUserIdsByRoleId(role.getId());
    }

    /**
     * Punto unico de asignacion: valida duplicado, persiste y publica {@link RoleAssignedEvent}.
     * La existencia del rol ya la garantizaron las sobrecargas publicas de assignRole, asi que ningun
     * llamador la revalida.
     *
     * <p>El evento se publica aqui (y no en cada llamador) para que TODA via de asignacion —registro,
     * auto-asignacion RF-010/011 y concesion por admin RF-065— inicialice las filas 1-a-1 asociadas
     * (metrics, offerer_profile) sin duplicar logica. Los listeners corren BEFORE_COMMIT, dentro de
     * esta misma transaccion.</p>
     */
    private void doAssign(Long userId, Integer roleId, RoleName roleName) {
        if (userRolePersistencePort.existsByUserIdAndRoleId(userId, roleId)) {
            throw new InvalidStateException("User " + userId + " already has role id: " + roleId);
        }
        userRolePersistencePort.assignRole(userId, roleId);
        domainEventPublisher.publish(new RoleAssignedEvent(userId, roleName.name()));
    }

    /** Las claves de la tabla roles son INT (Integer); las firmas de entrada usan Long. */
    private Integer toRoleId(Long roleId) {
        if (roleId == null) {
            throw new InvalidStateException("roleId is required");
        }
        return roleId.intValue();
    }

    /**
     * RF-010/011: auto-asignacion de un rol publico. ADMIN queda excluido por regla de negocio: la
     * unica via legitima de obtenerlo es que otro administrador lo conceda (RF-065).
     *
     * <p>Atomica: la fila de user_roles y las filas 1-a-1 que disparan sus listeners
     * (offerer_profile, metricas) se crean en la misma transaccion o no se crea nada.</p>
     */
    @Override
    @Transactional
    public void acquireRole(Long userId, String roleName) {
        RoleName target = parseRole(roleName);
        if (target == RoleName.ADMIN) {
            throw new InvalidStateException("Cannot self-assign the ADMIN role");
        }
        // Existencia + duplicado + persistencia + evento centralizados en assignRole (por nombre).
        assignRole(userId, target);
    }

    private RoleName parseRole(String roleName) {
        try {
            return RoleName.valueOf(roleName == null ? "" : roleName.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new InvalidStateException("Invalid role: " + roleName);
        }
    }
}
