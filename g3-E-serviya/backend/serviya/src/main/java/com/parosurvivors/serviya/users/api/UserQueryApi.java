package com.parosurvivors.serviya.users.api;

import com.parosurvivors.serviya.shared.contract.PageResult;
import com.parosurvivors.serviya.users.api.dto.RoleSnapshot;
import com.parosurvivors.serviya.users.api.dto.UserRoleAssignmentSnapshot;
import com.parosurvivors.serviya.users.api.dto.UserSearchCriteria;
import com.parosurvivors.serviya.users.api.dto.UserSnapshot;
import com.parosurvivors.serviya.users.api.dto.UserSummary;
import java.util.List;
import java.util.Optional;

/**
 * Consultas publicas de identity.
 */
public interface UserQueryApi {

    Optional<UserSnapshot> findById(Long userId);

    PageResult<UserSummary> search(UserSearchCriteria criteria, int page, int size);

    /** Catalogo completo de roles del sistema. */
    List<RoleSnapshot> getAllRoles();

    List<RoleSnapshot> getRoles(Long userId);

    List<UserRoleAssignmentSnapshot> getRoleAssignments(Long userId);

    List<Long> findUserIdsByRole(String roleName);

    boolean hasRole(Long userId, String roleName);
}
