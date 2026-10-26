package com.parosurvivors.serviya.requests.infrastructure.adapters.input.api;

import com.parosurvivors.serviya.requests.infrastructure.dto.form.CreateServiceRequestForm;
import com.parosurvivors.serviya.requests.infrastructure.dto.form.RescheduleRequestForm;
import com.parosurvivors.serviya.requests.infrastructure.dto.response.RequestHistoryResponse;
import com.parosurvivors.serviya.requests.infrastructure.dto.response.ServiceRequestDetailResponse;
import com.parosurvivors.serviya.requests.infrastructure.dto.response.ServiceRequestResponse;
import com.parosurvivors.serviya.requests.infrastructure.dto.response.ServiceRequestSummaryResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Documentacion OpenAPI/Swagger de solicitudes de servicio: consultas y transiciones de estado
 * (modulo 4, seccion 10). Ver estructura-endpoints.md. Convencion: docs aqui; binding y @Parameter en el controller.
 */
@Tag(name = "Solicitudes de servicio", description = "Consultas y maquina de estados de las solicitudes")
@SecurityRequirement(name = "bearerAuth")
public interface ServiceRequestApi {

    @Operation(summary = "Listar las solicitudes propias como cliente",
            description = "Devuelve un resumen enriquecido (servicio + contraparte). Filtros dinamicos opcionales: "
                    + "statuses (coma), serviceId, categoryId, counterpartyId, titleQuery (titulo del servicio), "
                    + "scheduledFrom/To y createdFrom/To. Orden por scheduledDate (por defecto) o createdAt via "
                    + "?sort. RF-030, RF-032, RF-038.")
    @ApiResponse(responseCode = "200", description = "Pagina de solicitudes (resumen)")
    ResponseEntity<Page<ServiceRequestSummaryResponse>> getClientRequests(
            List<String> statuses, Long serviceId, Long categoryId, Long counterpartyId, String titleQuery,
            LocalDateTime scheduledFrom, LocalDateTime scheduledTo, LocalDateTime createdFrom, LocalDateTime createdTo,
            Pageable pageable);

    @Operation(summary = "Listar las solicitudes propias como oferente",
            description = "Devuelve un resumen enriquecido (servicio + contraparte). Mismos filtros dinamicos y "
                    + "orden que la lista de cliente. RF-016, RF-020, RF-039.")
    @ApiResponse(responseCode = "200", description = "Pagina de solicitudes (resumen)")
    ResponseEntity<Page<ServiceRequestSummaryResponse>> getOffererRequests(
            List<String> statuses, Long serviceId, Long categoryId, Long counterpartyId, String titleQuery,
            LocalDateTime scheduledFrom, LocalDateTime scheduledTo, LocalDateTime createdFrom, LocalDateTime createdTo,
            Pageable pageable);

    @Operation(summary = "Detalle de una solicitud para una de las partes", description = "RF-030.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Detalle de la solicitud"),
            @ApiResponse(responseCode = "403", description = "El solicitante no es parte de la solicitud")
    })
    ResponseEntity<ServiceRequestDetailResponse> getRequestDetail(Long id);

    @Operation(summary = "Historial de reprogramaciones de una solicitud",
            description = "Recorre la cadena previousRequestId. Solo accesible para el cliente/oferente "
                    + "participante o un admin.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Cadena de solicitudes enlazadas"),
            @ApiResponse(responseCode = "403", description = "El solicitante no es parte de la solicitud ni admin")
    })
    ResponseEntity<List<RequestHistoryResponse>> getRequestHistory(Long id);

    @Operation(summary = "Crear una solicitud de servicio",
            description = "Valida servicio activo, disponibilidad y radio. RF-029.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Solicitud creada"),
            @ApiResponse(responseCode = "422", description = "Validacion de negocio fallida (radio/disponibilidad)")
    })
    ResponseEntity<ServiceRequestResponse> createRequest(CreateServiceRequestForm form);

    @Operation(summary = "Aceptar una solicitud (oferente)", description = "RF-017.")
    @ApiResponse(responseCode = "204", description = "Solicitud aceptada")
    ResponseEntity<Void> acceptRequest(Long id);

    @Operation(summary = "Rechazar una solicitud (oferente)", description = "RF-018.")
    @ApiResponse(responseCode = "204", description = "Solicitud rechazada")
    ResponseEntity<Void> rejectRequest(Long id);

    @Operation(summary = "Cancelar una solicitud", description = "RF-022, RF-031.")
    @ApiResponse(responseCode = "204", description = "Solicitud cancelada")
    ResponseEntity<Void> cancelRequest(Long id);

    @Operation(summary = "Marcar como presuntamente cumplida (oferente)", description = "RF-019.")
    @ApiResponse(responseCode = "204", description = "Solicitud marcada como presuntamente cumplida")
    ResponseEntity<Void> markCompleted(Long id);

    @Operation(summary = "Confirmar la realizacion del servicio (cliente)", description = "RF-037, RF-020.")
    @ApiResponse(responseCode = "204", description = "Servicio confirmado como completado")
    ResponseEntity<Void> confirmCompletion(Long id);

    @Operation(summary = "Reprogramar una solicitud (cliente)",
            description = "Crea una nueva solicitud enlazada via previousRequestId. RF-033.")
    @ApiResponse(responseCode = "201", description = "Nueva solicitud enlazada creada")
    ResponseEntity<ServiceRequestResponse> rescheduleRequest(Long id, RescheduleRequestForm form);

    @Operation(summary = "Listar las solicitudes propias como cliente en agenda",
            description = "Filtrables por estados. RF-030, RF-032, RF-038.")
    @ApiResponse(responseCode = "200", description = "Pagina de solicitudes")
    ResponseEntity<Page<ServiceRequestResponse>> getClientAgenda(Pageable pageable);


    @Operation(summary = "Listar las solicitudes propias como oferente en agenda",
            description = "Filtrables por estados. RF-030, RF-032, RF-038.")
    @ApiResponse(responseCode = "200", description = "Pagina de solicitudes")
    ResponseEntity<Page<ServiceRequestResponse>> getOffererAgenda(Pageable pageable);
}