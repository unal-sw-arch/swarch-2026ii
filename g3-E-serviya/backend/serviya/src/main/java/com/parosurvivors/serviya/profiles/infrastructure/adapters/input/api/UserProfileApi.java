package com.parosurvivors.serviya.profiles.infrastructure.adapters.input.api;

import com.parosurvivors.serviya.profiles.infrastructure.dto.form.UpdateMainAddressForm;
import com.parosurvivors.serviya.profiles.infrastructure.dto.form.UpdateProfileForm;
import com.parosurvivors.serviya.profiles.infrastructure.dto.response.UserProfileResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;

/**
 * Documentacion OpenAPI/Swagger del perfil personal del usuario autenticado (modulo 2, seccion 3).
 * Ver estructura-endpoints.md. Convencion: docs aqui; binding y @Parameter en el controller.
 */
@Tag(name = "Perfil personal", description = "Informacion personal del usuario autenticado (/me)")
@SecurityRequirement(name = "bearerAuth")
public interface UserProfileApi {

    @Operation(summary = "Obtener la informacion personal propia",
            description = "Incluye los campos cifrados ya desencriptados. RF-005.")
    @ApiResponse(responseCode = "200", description = "Informacion del perfil")
    ResponseEntity<UserProfileResponse> getProfileInfo();

    @Operation(summary = "Actualizar parcialmente el perfil (PATCH)",
            description = "Solo los campos no nulos se modifican. documentType/documentNumber no editables. RF-006.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Perfil actualizado"),
            @ApiResponse(responseCode = "400", description = "Datos invalidos")
    })
    ResponseEntity<UserProfileResponse> patchProfile(UpdateProfileForm form);

    @Operation(summary = "Cambiar la direccion principal",
            description = "Elige cual direccion existente es la principal. Verifica propiedad.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Direccion principal actualizada"),
            @ApiResponse(responseCode = "403", description = "La direccion no pertenece al usuario")
    })
    ResponseEntity<Void> updateMainAddress(UpdateMainAddressForm form);
}
