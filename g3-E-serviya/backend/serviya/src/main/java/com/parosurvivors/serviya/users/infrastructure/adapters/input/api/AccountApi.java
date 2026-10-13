package com.parosurvivors.serviya.users.infrastructure.adapters.input.api;

import com.parosurvivors.serviya.users.infrastructure.dto.form.ChangeEmailForm;
import com.parosurvivors.serviya.users.infrastructure.dto.form.ChangePasswordForm;
import com.parosurvivors.serviya.users.infrastructure.dto.response.AuthResponse;
import com.parosurvivors.serviya.users.infrastructure.dto.response.RoleResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;

import java.util.List;

/**
 * Documentacion OpenAPI/Swagger del controlador de gestion de cuenta del usuario autenticado (modulo 2).
 * El id del usuario se extrae del JWT (/me). Ver estructura-endpoints.md (seccion 2).
 * Convencion: docs de metodo aqui; binding y @Parameter van en el controller.
 */
@Tag(name = "Cuenta", description = "Gestion de la cuenta del usuario autenticado (/me)")
@SecurityRequirement(name = "bearerAuth")
public interface AccountApi {

    @Operation(summary = "Cambiar la contrasena propia", description = "RF-007.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Contrasena actualizada"),
            @ApiResponse(responseCode = "400", description = "Contrasena actual incorrecta")
    })
    ResponseEntity<Void> changePassword(ChangePasswordForm form);

    @Operation(summary = "Cambiar el correo propio", description = "RF-007.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Correo actualizado"),
            @ApiResponse(responseCode = "409", description = "El correo ya esta en uso")
    })
    ResponseEntity<Void> changeEmail(ChangeEmailForm form);

    @Operation(summary = "Eliminar la cuenta propia (soft delete)",
            description = "Orquesta la eliminacion: cancela solicitudes y desactiva servicios. RF-008.")
    @ApiResponse(responseCode = "204", description = "Cuenta eliminada")
    ResponseEntity<Void> deleteOwnAccount();

    @Operation(summary = "Adquirir el rol OFFERER (auto-servicio)",
            description = "RF-010. Crea el perfil de oferente y sus metricas en la misma transaccion, "
                    + "y devuelve un JWT NUEVO que ya incluye el rol (acceso inmediato, sin re-login).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Rol asignado; se devuelve el token actualizado"),
            @ApiResponse(responseCode = "409", description = "El usuario ya tiene el rol")
    })
    ResponseEntity<AuthResponse> acquireOffererRole();

    @Operation(summary = "Adquirir el rol CLIENT (auto-servicio)",
            description = "RF-011. Inicializa las metricas de cliente en la misma transaccion, y devuelve "
                    + "un JWT NUEVO que ya incluye el rol (acceso inmediato, sin re-login).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Rol asignado; se devuelve el token actualizado"),
            @ApiResponse(responseCode = "409", description = "El usuario ya tiene el rol")
    })
    ResponseEntity<AuthResponse> acquireClientRole();

    @Operation(summary = "Listar los roles propios")
    @ApiResponse(responseCode = "200", description = "Roles del usuario")
    ResponseEntity<List<RoleResponse>> getOwnRoles();
}
