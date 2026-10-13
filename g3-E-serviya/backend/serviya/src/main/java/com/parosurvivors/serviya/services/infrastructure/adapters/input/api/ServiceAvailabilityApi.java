package com.parosurvivors.serviya.services.infrastructure.adapters.input.api;

import java.util.List;

import org.springframework.http.ResponseEntity;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

import com.parosurvivors.serviya.services.infrastructure.dto.form.CreateServiceAvailabilityForm;
import com.parosurvivors.serviya.services.infrastructure.dto.form.UpdateServiceAvailabilityForm;
import com.parosurvivors.serviya.services.infrastructure.dto.response.ServiceAvailabilityResponse;

@Tag(name = "Service Availability", description = "API de gestión de disponibilidad de servicios")
public interface ServiceAvailabilityApi {
    
    @Operation(summary = "Crear una nueva disponibilidad para un servicio")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Disponibilidad creada exitosamente"),
        @ApiResponse(responseCode = "400", description = "Datos inválidos")
    })
    ResponseEntity<ServiceAvailabilityResponse> create(Long serviceId, CreateServiceAvailabilityForm form);

    @Operation(summary = "Eliminar una disponibilidad de un servicio")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Disponibilidad eliminada exitosamente"),
        @ApiResponse(responseCode = "404", description = "Disponibilidad no encontrada")
    })
    ResponseEntity<Void> delete(Long id);

    @Operation(summary = "Actualizar una disponibilidad de un servicio")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Disponibilidad actualizada exitosamente"),
        @ApiResponse(responseCode = "400", description = "Datos inválidos"),
        @ApiResponse(responseCode = "404", description = "Disponibilidad no encontrada")
    })
    ResponseEntity<ServiceAvailabilityResponse> update(Long id, UpdateServiceAvailabilityForm form);
    
    @Operation(summary = "Obtener las disponibilidades de un servicio")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Disponibilidades obtenidas exitosamente"),
        @ApiResponse(responseCode = "404", description = "Servicio no encontrado")
    })
    ResponseEntity<List<ServiceAvailabilityResponse>> getByServiceId(Long serviceId);

    @Operation(summary = "Aplicar la plantilla general del oferente al servicio")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Plantilla aplicada exitosamente")
    })
    ResponseEntity<Void> applyGeneralTemplate(Long serviceId);
}
