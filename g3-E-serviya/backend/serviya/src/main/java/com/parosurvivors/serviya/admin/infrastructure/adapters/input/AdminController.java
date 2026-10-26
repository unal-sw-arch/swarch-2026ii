package com.parosurvivors.serviya.admin.infrastructure.adapters.input;

import com.parosurvivors.serviya.users.application.dto.query.SearchUsersQuery;
import com.parosurvivors.serviya.admin.application.dto.query.AdminFeedbackSearchQuery;
import com.parosurvivors.serviya.admin.application.ports.input.AdminServicePort;
import com.parosurvivors.serviya.admin.infrastructure.adapters.input.api.AdminApi;
import com.parosurvivors.serviya.admin.infrastructure.dto.form.BanUserForm;
import com.parosurvivors.serviya.admin.infrastructure.dto.form.GrantRoleForm;
import com.parosurvivors.serviya.admin.infrastructure.dto.form.CreateUserByAdminForm;
import com.parosurvivors.serviya.admin.infrastructure.dto.form.UpdateUserByAdminForm;
import com.parosurvivors.serviya.admin.infrastructure.dto.response.AdminFeedbackSearchResponse;
import com.parosurvivors.serviya.admin.infrastructure.dto.response.UserAdminDetailResponse;
import com.parosurvivors.serviya.admin.infrastructure.dto.response.UserRoleAssignmentResponse;
import com.parosurvivors.serviya.admin.infrastructure.dto.response.UserSummaryResponse;
import com.parosurvivors.serviya.admin.infrastructure.mappers.AdminWebMapper;
import com.parosurvivors.serviya.requests.infrastructure.dto.response.AdminRequestDetailResponse;
import com.parosurvivors.serviya.requests.infrastructure.mappers.ServiceRequestWebMapper;
import com.parosurvivors.serviya.shared.security.CurrentUser;
import com.parosurvivors.serviya.users.application.ports.input.RoleServicePort;
import com.parosurvivors.serviya.users.application.ports.input.UserRoleServicePort;
import com.parosurvivors.serviya.users.infrastructure.dto.response.RoleResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Adaptador de entrada (REST) de administracion de usuarios y roles. Placeholder funcional;
 * documentacion en {@link AdminApi}. Mapea Form/Query->Command/Query y dominio/Result->Response.
 */
@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class AdminController implements AdminApi {

    private final AdminServicePort adminService;
    private final UserRoleServicePort userRoleService;
    private final RoleServicePort roleService;
    private final AdminWebMapper mapper;
    private final ServiceRequestWebMapper serviceRequestWebMapper;

    @Override
    @GetMapping("/users")
    public ResponseEntity<Page<UserSummaryResponse>> searchUsers(SearchUsersQuery filters, Pageable pageable) {
        return ResponseEntity.ok(adminService.searchUsers(filters, pageable).map(mapper::toResponse));
    }

    @Override
    @GetMapping("/users/{id}")
    public ResponseEntity<UserAdminDetailResponse> getUserAdminDetail(@PathVariable Long id) {
        return ResponseEntity.ok(mapper.toResponse(adminService.getUserAdminDetail(id)));
    }

    @Override
    @GetMapping("/service-requests/{id}")
    public ResponseEntity<AdminRequestDetailResponse> getRequestDetailForAdmin(@PathVariable Long id) {
        // Vista administrativa (ambas partes). El gate de rol ADMIN lo aplica SecurityConfig (/api/v1/admin/**).
        return ResponseEntity.ok(serviceRequestWebMapper.toResponse(adminService.getRequestDetailForAdmin(id)));
    }

    @Override
    @PostMapping("/users")
    public ResponseEntity<UserSummaryResponse> createUserByAdmin(@Valid @RequestBody CreateUserByAdminForm form) {
        var created = adminService.createUserByAdmin(mapper.toCommand(form, CurrentUser.id()));
        return ResponseEntity.status(HttpStatus.CREATED).body(mapper.toResponse(created));
    }

    @Override
    @PostMapping("/users/{id}/ban")
    public ResponseEntity<Void> banUser(@PathVariable Long id, @Valid @RequestBody BanUserForm form) {
        adminService.banUser(CurrentUser.id(), id, form.reason());
        return ResponseEntity.noContent().build();
    }

    @Override
    @PostMapping("/users/{id}/unban")
    public ResponseEntity<Void> unbanUser(@PathVariable Long id) {
        adminService.unbanUser(CurrentUser.id(), id);
        return ResponseEntity.noContent().build();
    }

    @Override
    @DeleteMapping("/users/{id}")
    public ResponseEntity<Void> deleteUser(@PathVariable Long id) {
        adminService.deleteUser(CurrentUser.id(), id);
        return ResponseEntity.noContent().build();
    }

    @Override
    @GetMapping("/roles")
    public ResponseEntity<List<RoleResponse>> getRoles() {
        return ResponseEntity.ok(mapper.toRoleResponses(roleService.getRoles()));
    }

    /** RF-067: roles actuales del usuario CON la fecha en que se concedio cada uno. */
    @Override
    @GetMapping("/users/{id}/roles")
    public ResponseEntity<List<UserRoleAssignmentResponse>> getUserRoles(@PathVariable Long id) {
        return ResponseEntity.ok(mapper.toAssignmentResponses(userRoleService.getUserRoleAssignments(id)));
    }

    /** RF-068: edicion parcial de un usuario por el administrador. */
    @Override
    @PatchMapping("/users/{id}")
    public ResponseEntity<Void> updateUserByAdmin(@PathVariable Long id,
                                                  @Valid @RequestBody UpdateUserByAdminForm form) {
        adminService.updateUserByAdmin(CurrentUser.id(), id, mapper.toCommand(form));
        return ResponseEntity.noContent().build();
    }

    @Override
    @PostMapping("/users/{id}/roles")
    public ResponseEntity<Void> grantRoleByAdmin(@PathVariable Long id, @Valid @RequestBody GrantRoleForm form) {
        adminService.grantRoleByAdmin(CurrentUser.id(), id, form.role());
        return ResponseEntity.noContent().build();
    }

    /**
     * RF-066. El rol se identifica por NOMBRE (CLIENT/OFFERER/ADMIN), que es como razona el admin.
     * Delega en AdminService porque retirar un rol arrastra una cascada conservadora (desactivar
     * servicios y cancelar solicitudes de ese rol, notificando a las contrapartes).
     */
    @Override
    @DeleteMapping("/users/{id}/roles/{role}")
    public ResponseEntity<Void> removeRole(@PathVariable Long id, @PathVariable String role) {
        adminService.revokeRoleByAdmin(CurrentUser.id(), id, role);
        return ResponseEntity.noContent().build();
    }

    @Override
    @GetMapping("/feedback")
    public ResponseEntity<Page<AdminFeedbackSearchResponse>> searchFeedback(
            Long clientId, Long offererId, Long serviceId, String keyword,
            Integer ratingMin, Integer ratingMax, Pageable pageable) {
        AdminFeedbackSearchQuery query = new AdminFeedbackSearchQuery(clientId, offererId, serviceId, keyword, ratingMin, ratingMax);
        return ResponseEntity.ok(adminService.searchFeedback(query, pageable).map(mapper::toFeedbackResponse));
    }

    @Override
    @DeleteMapping("/services/{id}")
    public ResponseEntity<Void> deleteService(@PathVariable Long id) {
        adminService.deleteService(id, CurrentUser.id(), CurrentUser.isAdmin());
        return ResponseEntity.noContent().build();
    }
}
