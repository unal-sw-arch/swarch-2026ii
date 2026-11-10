package com.parosurvivors.serviya.users.application.api;

import com.parosurvivors.serviya.shared.contract.PageResult;
import com.parosurvivors.serviya.shared.exceptions.ResourceNotFoundException;
import com.parosurvivors.serviya.users.api.UserCommandApi;
import com.parosurvivors.serviya.users.api.UserContactApi;
import com.parosurvivors.serviya.users.api.UserQueryApi;
import com.parosurvivors.serviya.users.api.dto.CreateUserAccountCommand;
import com.parosurvivors.serviya.users.api.dto.RoleSnapshot;
import com.parosurvivors.serviya.users.api.dto.UserRoleAssignmentSnapshot;
import com.parosurvivors.serviya.users.api.dto.UserSearchCriteria;
import com.parosurvivors.serviya.users.api.dto.UserSnapshot;
import com.parosurvivors.serviya.users.api.dto.UserSummary;
import com.parosurvivors.serviya.users.application.dto.command.ChangeEmailCommand;
import com.parosurvivors.serviya.users.application.dto.command.ChangePasswordCommand;
import com.parosurvivors.serviya.users.application.dto.item.UserSummaryItem;
import com.parosurvivors.serviya.users.application.dto.query.SearchUsersQuery;
import com.parosurvivors.serviya.users.application.ports.input.UserCreationServicePort;
import com.parosurvivors.serviya.users.application.ports.input.UserDeletionServicePort;
import com.parosurvivors.serviya.users.application.ports.input.UserQueryServicePort;
import com.parosurvivors.serviya.users.application.ports.input.UserRoleServicePort;
import com.parosurvivors.serviya.users.application.ports.input.UserServicePort;
import com.parosurvivors.serviya.users.application.ports.input.RoleServicePort;
import com.parosurvivors.serviya.users.domain.Role;
import com.parosurvivors.serviya.users.domain.RoleName;
import com.parosurvivors.serviya.users.domain.User;
import com.parosurvivors.serviya.users.domain.UserRoleAssignment;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

/**
 * Implementacion de los contratos publicos de identity sobre los casos de uso existentes.
 *
 * <p>Es un adaptador de frontera: no contiene reglas de negocio, solo traduce entre el dominio
 * interno ({@link User}, {@link Role}, enums de rol) y los DTOs publicos. Mantiene la API libre de
 * entidades y enums internos.</p>
 */
@Component
@RequiredArgsConstructor
public class UserApiFacade implements UserQueryApi, UserCommandApi {

    private final UserQueryServicePort userQueryServicePort;
    private final UserServicePort userServicePort;
    private final UserCreationServicePort userCreationServicePort;
    private final UserRoleServicePort userRoleServicePort;
    private final UserDeletionServicePort userDeletionServicePort;
    private final RoleServicePort roleServicePort;

    // ---------------------------------------------------------------------
    // UserQueryApi
    // ---------------------------------------------------------------------

    @Override
    public Optional<UserSnapshot> findById(Long userId) {
        try {
            return Optional.of(toSnapshot(userQueryServicePort.getUserById(userId)));
        } catch (ResourceNotFoundException ex) {
            return Optional.empty();
        }
    }

    @Override
    public PageResult<UserSummary> search(UserSearchCriteria criteria, int page, int size) {
        SearchUsersQuery query = new SearchUsersQuery(
                criteria == null ? null : criteria.email(),
                criteria == null ? null : criteria.fullName(),
                criteria == null ? null : criteria.role(),
                criteria == null ? null : criteria.banned(),
                criteria == null ? null : criteria.deleted());

        Page<UserSummaryItem> result = userQueryServicePort.searchUsers(query, PageRequest.of(page, size));
        return new PageResult<>(
                result.getContent().stream().map(this::toSummary).toList(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages());
    }

    @Override
    public List<RoleSnapshot> getAllRoles() {
        return roleServicePort.getRoles().stream()
                .map(this::toRoleSnapshot)
                .toList();
    }

    @Override
    public List<RoleSnapshot> getRoles(Long userId) {
        return userRoleServicePort.getUserRoles(userId).stream()
                .map(this::toRoleSnapshot)
                .toList();
    }

    @Override
    public List<UserRoleAssignmentSnapshot> getRoleAssignments(Long userId) {
        return userRoleServicePort.getUserRoleAssignments(userId).stream()
                .map(this::toAssignmentSnapshot)
                .toList();
    }

    @Override
    public List<Long> findUserIdsByRole(String roleName) {
        return userRoleServicePort.findUserIdsByRole(toRoleName(roleName));
    }

    @Override
    public boolean hasRole(Long userId, String roleName) {
        return userRoleServicePort.hasRole(userId, roleName);
    }

    // ---------------------------------------------------------------------
    // UserCommandApi
    // ---------------------------------------------------------------------

    @Override
    public UserSnapshot createUserAccount(CreateUserAccountCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("CreateUserAccountCommand is required");
        }
        User created = userCreationServicePort.createUserAccount(
                new com.parosurvivors.serviya.users.application.dto.command.CreateUserAccountCommand(
                        command.email(),
                        command.password(),
                        command.fullName(),
                        command.role(),
                        command.documentType(),
                        command.documentNumber(),
                        command.phone(),
                        command.acceptedTerms(),
                        command.addressLine(),
                        command.city(),
                        command.latitude(),
                        command.longitude()));
        return toSnapshot(created);
    }

    @Override
    public void changeEmail(Long userId, String newEmail) {
        userServicePort.changeEmail(new ChangeEmailCommand(userId, newEmail));
    }

    @Override
    public void changePassword(Long userId, String currentPassword, String newPassword) {
        userServicePort.changePassword(new ChangePasswordCommand(userId, currentPassword, newPassword));
    }

    @Override
    public void banUser(Long userId, String reason) {
        userServicePort.banUser(userId, reason);
    }

    @Override
    public void unbanUser(Long userId) {
        userServicePort.unbanUser(userId);
    }

    @Override
    public void assignRole(Long userId, String roleName) {
        userRoleServicePort.assignRole(userId, toRoleName(roleName));
    }

    @Override
    public void revokeRole(Long userId, String roleName) {
        userRoleServicePort.revokeRole(userId, toRoleName(roleName));
    }

    @Override
    public void removeRole(Long userId, Long roleId) {
        userRoleServicePort.removeRole(userId, roleId);
    }

    @Override
    public void deleteUser(Long userId) {
        userDeletionServicePort.deleteUser(userId);
    }

    // ---------------------------------------------------------------------
    // Mapping
    // ---------------------------------------------------------------------

    private UserSnapshot toSnapshot(User user) {
        return new UserSnapshot(
                user.getId(),
                user.getEmail(),
                user.isBanned(),
                user.isDeleted(),
                user.getDeletedAt(),
                user.getCreatedAt(),
                toRoleNames(user));
    }

    private Set<String> toRoleNames(User user) {
        if (user.getRoles() == null || user.getRoles().isEmpty()) {
            return Set.of();
        }
        return user.getRoles().stream()
                .map(Enum::name)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private UserSummary toSummary(UserSummaryItem item) {
        return new UserSummary(
                item.id(),
                item.email(),
                Boolean.TRUE.equals(item.banned()),
                item.deletedAt(),
                item.createdAt());
    }

    private RoleSnapshot toRoleSnapshot(Role role) {
        return new RoleSnapshot(role.getId(), role.getName() == null ? null : role.getName().name());
    }

    private UserRoleAssignmentSnapshot toAssignmentSnapshot(UserRoleAssignment assignment) {
        return new UserRoleAssignmentSnapshot(
                assignment.roleId(),
                assignment.name() == null ? null : assignment.name().name(),
                assignment.assignedAt());
    }

    private RoleName toRoleName(String roleName) {
        if (roleName == null) {
            throw new IllegalArgumentException("roleName is required");
        }
        try {
            return RoleName.valueOf(roleName);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Unknown role: " + roleName, ex);
        }
    }
}
