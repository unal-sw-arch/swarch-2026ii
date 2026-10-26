package com.parosurvivors.serviya.admin.infrastructure.adapters.input.api;

import com.parosurvivors.serviya.users.application.dto.query.SearchUsersQuery;
import com.parosurvivors.serviya.admin.infrastructure.dto.form.BanUserForm;
import com.parosurvivors.serviya.admin.infrastructure.dto.form.GrantRoleForm;
import com.parosurvivors.serviya.admin.infrastructure.dto.form.CreateUserByAdminForm;
import com.parosurvivors.serviya.admin.infrastructure.dto.response.AdminFeedbackSearchResponse;
import com.parosurvivors.serviya.admin.infrastructure.dto.response.UserAdminDetailResponse;
import com.parosurvivors.serviya.admin.infrastructure.dto.form.UpdateUserByAdminForm;
import com.parosurvivors.serviya.admin.infrastructure.dto.response.UserRoleAssignmentResponse;
import com.parosurvivors.serviya.admin.infrastructure.dto.response.UserSummaryResponse;
import com.parosurvivors.serviya.requests.infrastructure.dto.response.AdminRequestDetailResponse;
import com.parosurvivors.serviya.users.infrastructure.dto.response.RoleResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;

import java.util.List;

/**
 * Documentacion OpenAPI/Swagger de la administracion de usuarios y roles (modulo 9, seccion 17).
 * Ver estructura-endpoints.md. Todas requieren rol ADMIN. Convencion: docs aqui; binding y @Parameter en el controller.
 */
@Tag(name = "Administracion", description = "Gestion de usuarios y roles por un admin")
@SecurityRequirement(name = "bearerAuth")
public interface AdminApi {

    @Operation(summary = "Buscar usuarios con filtros", description = "RF-068.")
    @ApiResponse(responseCode = "200", description = "Pagina de usuarios")
    ResponseEntity<Page<UserSummaryResponse>> searchUsers(SearchUsersQuery filters, Pageable pageable);

    @Operation(summary = "Detalle de un usuario para el panel admin", description = "RF-081.")
    @ApiResponse(responseCode = "200", description = "Detalle del usuario")
    ResponseEntity<UserAdminDetailResponse> getUserAdminDetail(Long id);

    @Operation(summary = "Detalle administrativo de una solicitud (ambas partes)",
            description = "Vista completa cliente+oferente de una solicitud, para el panel admin.")
    @ApiResponse(responseCode = "200", description = "Detalle de la solicitud")
    ResponseEntity<AdminRequestDetailResponse> getRequestDetailForAdmin(Long id);

    @Operation(summary = "Crear un usuario con cualquier rol, incluido ADMIN (cuenta nueva)")
    @ApiResponse(responseCode = "201", description = "Usuario creado")
    ResponseEntity<UserSummaryResponse> createUserByAdmin(CreateUserByAdminForm form);

    @Operation(summary = "Banear a un usuario", description = "RF-069, RF-063. El motivo (obligatorio) se "
            + "comunica al usuario baneado por doble canal (interno + email).")
    @ApiResponse(responseCode = "204", description = "Usuario baneado")
    ResponseEntity<Void> banUser(Long id, BanUserForm form);

    @Operation(summary = "Desbanear a un usuario", description = "RF-070, RF-075.")
    @ApiResponse(responseCode = "204", description = "Usuario desbaneado")
    ResponseEntity<Void> unbanUser(Long id);

    @Operation(summary = "Eliminar a un usuario", description = "RF-068.")
    @ApiResponse(responseCode = "204", description = "Usuario eliminado")
    ResponseEntity<Void> deleteUser(Long id);

    @Operation(summary = "Listar todos los roles del sistema")
    @ApiResponse(responseCode = "200", description = "Roles disponibles")
    ResponseEntity<List<RoleResponse>> getRoles();

    @Operation(summary = "Listar los roles de un usuario", description = "RF-067.")
    @ApiResponse(responseCode = "200", description = "Roles del usuario")
    ResponseEntity<List<UserRoleAssignmentResponse>> getUserRoles(Long id);

    @Operation(summary = "Editar parcialmente un usuario",
            description = "RF-068. Solo se actualizan los campos enviados. El documento es inmutable y "
                    + "no se puede cambiar ni desde el panel admin. La PII (telefono) se cifra al persistir.")
    @ApiResponse(responseCode = "204", description = "Usuario actualizado")
    ResponseEntity<Void> updateUserByAdmin(Long id, UpdateUserByAdminForm form);

    @Operation(summary = "Conceder un rol a un usuario existente (cualquier rol, incl. ADMIN)",
            description = "RF-065. El rol va por nombre en el body.")
    @ApiResponse(responseCode = "204", description = "Rol concedido")
    ResponseEntity<Void> grantRoleByAdmin(Long id, GrantRoleForm form);

    @Operation(summary = "Quitar un rol a un usuario", description = "RF-066.")
    @ApiResponse(responseCode = "204", description = "Rol removido")
    // RF-066: el rol se identifica por NOMBRE (CLIENT/OFFERER/ADMIN), no por id numerico, que es como
    // razona un admin. Reemplaza a la variante anterior removeRole(Long id, Long roleId).
    ResponseEntity<Void> removeRole(Long id, String role);

    @Operation(summary = "Busqueda combinada de feedback (service_feedback + client_feedback)",
            description = "RF-048. Filtros opcionales: clientId, offererId, serviceId, keyword, ratingMin, ratingMax.")
    @ApiResponse(responseCode = "200", description = "Pagina de feedback unificado")
    ResponseEntity<Page<AdminFeedbackSearchResponse>> searchFeedback(
            Long clientId, Long offererId, Long serviceId, String keyword,
            Integer ratingMin, Integer ratingMax, Pageable pageable);

    @Operation(summary = "Eliminar un servicio del marketplace", description = "RF-064.")
    @ApiResponse(responseCode = "204", description = "Servicio eliminado")
    ResponseEntity<Void> deleteService(Long id);
}
