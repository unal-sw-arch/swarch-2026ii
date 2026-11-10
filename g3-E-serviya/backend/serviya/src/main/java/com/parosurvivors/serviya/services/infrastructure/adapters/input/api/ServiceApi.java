package com.parosurvivors.serviya.services.infrastructure.adapters.input.api;

import com.parosurvivors.serviya.services.infrastructure.dto.form.CreateServiceForm;
import com.parosurvivors.serviya.services.infrastructure.dto.form.UpdateServiceForm;
import com.parosurvivors.serviya.services.infrastructure.dto.response.ServiceResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * Documentacion OpenAPI/Swagger del controlador de servicios (gestion via MarketplaceService).
 * Ver estructura-endpoints.md (modulo 3, seccion 7). Convencion: docs de metodo aqui; las anotaciones
 * de binding (@PathVariable, @RequestBody, @Valid) y @Parameter viajan con el parametro en el controller.
 */
@Tag(name = "Services", description = "API de gestión de servicios del marketplace")
public interface ServiceApi {

    @Operation(summary = "Crear un nuevo servicio (OFFERER)")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Servicio creado exitosamente"),
            @ApiResponse(responseCode = "400", description = "Datos inválidos")
    })
    ResponseEntity<ServiceResponse> create(
            String title,
            String description,
            String priceHourly,
            String categoryId,
            String averageDurationMinutes,
            String operationRadiusKm,
            List<MultipartFile> photos);

    @Operation(summary = "Obtener un servicio por ID")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Servicio encontrado"),
            @ApiResponse(responseCode = "404", description = "Servicio no encontrado")
    })
    ResponseEntity<ServiceResponse> getById(Long id);

    @Operation(summary = "Listar todos los servicios activos")
    @ApiResponse(responseCode = "200", description = "Lista de servicios")
    ResponseEntity<List<ServiceResponse>> getAll();

    @Operation(summary = "Actualizar parcialmente un servicio (Dueño OFFERER)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Servicio actualizado"),
            @ApiResponse(responseCode = "404", description = "Servicio no encontrado"),
            @ApiResponse(responseCode = "400", description = "Datos inválidos")
    })
    ResponseEntity<ServiceResponse> update(
            Long id,
            String title,
            String description,
            String priceHourly,
            String categoryId,
            String averageDurationMinutes,
            String operationRadiusKm,
            List<MultipartFile> photos,
            List<String> existingPhotos,
            List<String> removedPhotos);

    @Operation(summary = "Eliminar un servicio (eliminación física)")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Servicio eliminado"),
            @ApiResponse(responseCode = "404", description = "Servicio no encontrado")
    })
    ResponseEntity<Void> delete(Long id);

    @Operation(summary = "Marcar servicio como eliminado (soft delete)")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Servicio marcado como eliminado"),
            @ApiResponse(responseCode = "404", description = "Servicio no encontrado")
    })
    ResponseEntity<Void> softDelete(Long id);

    @Operation(summary = "Activar un servicio")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Servicio activado"),
            @ApiResponse(responseCode = "404", description = "Servicio no encontrado")
    })
    ResponseEntity<Void> activate(Long id);

    @Operation(summary = "Desactivar un servicio")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Servicio desactivado"),
            @ApiResponse(responseCode = "404", description = "Servicio no encontrado")
    })
    ResponseEntity<Void> deactivate(Long id);

}
