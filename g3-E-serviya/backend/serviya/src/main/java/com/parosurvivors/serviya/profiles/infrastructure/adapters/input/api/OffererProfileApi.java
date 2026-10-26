package com.parosurvivors.serviya.profiles.infrastructure.adapters.input.api;

import com.parosurvivors.serviya.profiles.infrastructure.dto.form.UpdateOffererProfileForm;
import com.parosurvivors.serviya.profiles.infrastructure.dto.response.OffererProfileSummaryResponse;
import com.parosurvivors.serviya.profiles.infrastructure.dto.response.OffererPublicProfileDetailResponse;
import com.parosurvivors.serviya.profiles.infrastructure.dto.response.OffererPublicProfileResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;

/**
 * Documentacion OpenAPI/Swagger del perfil de oferente (modulo 2, seccion 5). Ver estructura-endpoints.md.
 * Convencion: docs aqui; binding y @Parameter en el controller.
 */
@Tag(name = "Perfil de oferente", description = "Perfil publico del oferente y edicion del propio")
public interface OffererProfileApi {

    @Operation(summary = "Obtener el perfil publico de un oferente", description = "RF-015, RF-027.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Perfil publico del oferente"),
            @ApiResponse(responseCode = "404", description = "Oferente no encontrado")
    })
    ResponseEntity<OffererPublicProfileResponse> getPublicProfile(Long id);

    @Operation(summary = "Obtener el perfil publico COMPLETO de un oferente",
            description = "RF-027. Endpoint PUBLICO (cliente, administrador o visitante sin sesion): "
                    + "identidad y foto, especialidad, descripcion, calificacion promedio, metricas de "
                    + "desempeño y los servicios ACTIVOS publicados. No expone PII sensible (documento/telefono).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Perfil publico completo"),
            @ApiResponse(responseCode = "404", description = "Oferente no encontrado")
    })
    ResponseEntity<OffererPublicProfileDetailResponse> getPublicProfileDetail(Long id);
    @Operation(summary = "Obtener el perfil publico propio del oferente autenticado")
    @ApiResponse(responseCode = "200", description = "Perfil propio del oferente")
    ResponseEntity<OffererPublicProfileResponse> getOwnPublicProfile();

    @Operation(summary = "Obtener el resumen del perfil de un oferente")
    @ApiResponse(responseCode = "200", description = "Resumen del perfil")
    ResponseEntity<OffererProfileSummaryResponse> getProfileSummary(Long id);

    @Operation(summary = "Actualizar parcialmente el perfil de oferente propio (PATCH)",
            description = "Campos editables: whatsappNumber, publicDescription, specialty. RF-015, RF-012.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Perfil actualizado"),
            @ApiResponse(responseCode = "400", description = "Datos invalidos")
    })
    ResponseEntity<OffererPublicProfileResponse> patchOffererProfile(UpdateOffererProfileForm form);
}
