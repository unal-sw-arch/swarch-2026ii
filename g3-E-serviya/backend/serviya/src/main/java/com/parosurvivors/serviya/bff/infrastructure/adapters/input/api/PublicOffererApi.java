package com.parosurvivors.serviya.bff.infrastructure.adapters.input.api;

import com.parosurvivors.serviya.bff.infrastructure.dto.response.OffererProfileSummaryResponse;
import com.parosurvivors.serviya.bff.infrastructure.dto.response.OffererPublicProfileDetailResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;

/**
 * Documentacion OpenAPI de las lecturas publicas compuestas del oferente.
 */
@Tag(name = "Vitrina publica de oferentes", description = "Perfil publico y resumen compuestos por el BFF")
public interface PublicOffererApi {

    @Operation(summary = "Obtener el perfil publico COMPLETO de un oferente",
            description = "RF-027. Endpoint publico: identidad, reputacion, metricas y servicios activos.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Perfil publico completo"),
            @ApiResponse(responseCode = "404", description = "Oferente no encontrado")
    })
    ResponseEntity<OffererPublicProfileDetailResponse> getPublicProfileDetail(Long id);

    @Operation(summary = "Obtener el resumen del perfil de un oferente")
    @ApiResponse(responseCode = "200", description = "Resumen del perfil")
    ResponseEntity<OffererProfileSummaryResponse> getProfileSummary(Long id);
}
