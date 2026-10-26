package com.parosurvivors.serviya.requests.infrastructure.adapters.input.api;

import com.parosurvivors.serviya.requests.infrastructure.dto.form.CreateRescheduleProposalForm;
import com.parosurvivors.serviya.requests.infrastructure.dto.response.RescheduleProposalDetailResponse;
import com.parosurvivors.serviya.requests.infrastructure.dto.response.RescheduleProposalResponse;
import com.parosurvivors.serviya.requests.infrastructure.dto.response.RescheduleProposalSummaryResponse;
import com.parosurvivors.serviya.requests.infrastructure.dto.response.ServiceRequestResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Documentacion OpenAPI/Swagger de propuestas de reprogramacion (modulo 4, seccion 11).
 * Ver estructura-endpoints.md. Convencion: los docs de metodo viven aqui; y para estos listados
 * (muchos query params) se unifica el metodo completo en la interfaz -docs + binding + @Parameter-
 * segun el escape de la convencion cuando separar por parametro resulta aparatoso.
 */
@Tag(name = "Propuestas de reprogramacion", description = "Propuestas del oferente al cliente para reprogramar")
@SecurityRequirement(name = "bearerAuth")
public interface RescheduleProposalApi {

    @Operation(summary = "Crear una propuesta de reprogramacion (oferente)", description = "RF-023.")
    @ApiResponse(responseCode = "201", description = "Propuesta creada (PENDING)")
    ResponseEntity<RescheduleProposalResponse> createProposal(CreateRescheduleProposalForm form);

    @Operation(summary = "Listar las propuestas recibidas (cliente)",
            description = "Paginado (created_at DESC), con filtros opcionales por estado, fechas y servicio. RF-034.")
    @ApiResponse(responseCode = "200", description = "Pagina de propuestas recibidas (resumen)")
    ResponseEntity<Page<RescheduleProposalSummaryResponse>> getProposalsReceived(
            @Parameter(description = "Filtrar por estados de la propuesta")
            @RequestParam(required = false) List<String> statuses,
            @Parameter(description = "Fecha propuesta desde (ISO-8601)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime proposedFrom,
            @Parameter(description = "Fecha propuesta hasta (ISO-8601)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime proposedTo,
            @Parameter(description = "Creada desde (ISO-8601)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime createdFrom,
            @Parameter(description = "Creada hasta (ISO-8601)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime createdTo,
            @Parameter(description = "Filtrar por servicio")
            @RequestParam(required = false) Long serviceId,
            Pageable pageable);

    @Operation(summary = "Listar las propuestas enviadas (oferente)",
            description = "Paginado (created_at DESC), con filtros opcionales por estado, fechas y servicio.")
    @ApiResponse(responseCode = "200", description = "Pagina de propuestas enviadas (resumen)")
    ResponseEntity<Page<RescheduleProposalSummaryResponse>> getProposalsSent(
            @Parameter(description = "Filtrar por estados de la propuesta")
            @RequestParam(required = false) List<String> statuses,
            @Parameter(description = "Fecha propuesta desde (ISO-8601)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime proposedFrom,
            @Parameter(description = "Fecha propuesta hasta (ISO-8601)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime proposedTo,
            @Parameter(description = "Creada desde (ISO-8601)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime createdFrom,
            @Parameter(description = "Creada hasta (ISO-8601)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime createdTo,
            @Parameter(description = "Filtrar por servicio")
            @RequestParam(required = false) Long serviceId,
            Pageable pageable);

    @Operation(summary = "Detalle de una propuesta (participante)",
            description = "Propuesta + solicitud + servicio + la otra parte (relativa a quien consulta). "
                    + "Solo para las partes de la solicitud.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Detalle de la propuesta"),
            @ApiResponse(responseCode = "403", description = "El solicitante no es parte de la solicitud"),
            @ApiResponse(responseCode = "404", description = "La propuesta no existe")
    })
    ResponseEntity<RescheduleProposalDetailResponse> getProposalDetail(Long id);

    @Operation(summary = "Listar el historial de propuestas de una solicitud",
            description = "Solo para las partes (cliente u oferente) de la solicitud.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Propuestas de la solicitud"),
            @ApiResponse(responseCode = "403", description = "El solicitante no es parte de la solicitud"),
            @ApiResponse(responseCode = "404", description = "La solicitud no existe")
    })
    ResponseEntity<List<RescheduleProposalResponse>> getProposalsByRequest(Long id);

    @Operation(summary = "Aceptar una propuesta (cliente)",
            description = "Acepta la fecha propuesta, dispara la reprogramacion y crea la nueva solicitud (ACCEPTED). RF-035.")
    @ApiResponse(responseCode = "200", description = "Nueva solicitud enlazada creada")
    ResponseEntity<ServiceRequestResponse> acceptProposal(Long id);

    @Operation(summary = "Rechazar una propuesta (cliente)", description = "RF-036.")
    @ApiResponse(responseCode = "204", description = "Propuesta rechazada")
    ResponseEntity<Void> rejectProposal(Long id);

    @Operation(summary = "Cancelar la propia propuesta (oferente)")
    @ApiResponse(responseCode = "204", description = "Propuesta cancelada")
    ResponseEntity<Void> cancelProposal(Long id);
}
