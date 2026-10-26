package com.parosurvivors.serviya.reports.infrastructure.adapters.input.api;

import com.parosurvivors.serviya.reports.infrastructure.dto.form.CreateClientFeedbackReportForm;
import com.parosurvivors.serviya.reports.infrastructure.dto.form.CreateRequestReportForm;
import com.parosurvivors.serviya.reports.infrastructure.dto.form.CreateServiceFeedbackReportForm;
import com.parosurvivors.serviya.reports.infrastructure.dto.response.ClientFeedbackReportResponse;
import com.parosurvivors.serviya.reports.infrastructure.dto.response.ReportActionResponse;
import com.parosurvivors.serviya.reports.infrastructure.dto.response.ReportDetailResponse;
import com.parosurvivors.serviya.reports.infrastructure.dto.response.ReportResponse;
import com.parosurvivors.serviya.reports.infrastructure.dto.response.RequestReportResponse;
import com.parosurvivors.serviya.reports.infrastructure.dto.response.ServiceFeedbackReportResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;

import java.util.List;

/**
 * Documentacion OpenAPI/Swagger de reportes: creacion por subtipo y consultas (modulo 7, seccion 15).
 * Ver estructura-endpoints.md. Convencion: docs aqui; binding y @Parameter en el controller.
 */
@Tag(name = "Reportes", description = "Creacion de reportes y consultas (consulta solo ADMIN)")
@SecurityRequirement(name = "bearerAuth")
public interface ReportApi {

    @Operation(summary = "Crear un reporte sobre una solicitud", description = "RF-055, RF-057, RF-073.")
    @ApiResponse(responseCode = "201", description = "Reporte de solicitud creado")
    ResponseEntity<RequestReportResponse> createRequestReport(CreateRequestReportForm form);

    @Operation(summary = "Crear un reporte sobre una resena de servicio", description = "RF-056.")
    @ApiResponse(responseCode = "201", description = "Reporte de resena de servicio creado")
    ResponseEntity<ServiceFeedbackReportResponse> createServiceFeedbackReport(CreateServiceFeedbackReportForm form);

    @Operation(summary = "Crear un reporte sobre una resena de cliente", description = "RF-056.")
    @ApiResponse(responseCode = "201", description = "Reporte de resena de cliente creado")
    ResponseEntity<ClientFeedbackReportResponse> createClientFeedbackReport(CreateClientFeedbackReportForm form);

    @Operation(summary = "Listar reportes para el panel admin", description = "RF-058.")
    @ApiResponse(responseCode = "200", description = "Pagina de reportes")
    ResponseEntity<Page<ReportResponse>> getReports(String type, String category, String status, Pageable pageable);

    @Operation(summary = "Detalle de un reporte (dispatch por tipo)", description = "RF-058.")
    @ApiResponse(responseCode = "200", description = "Detalle del reporte")
    ResponseEntity<ReportDetailResponse> getReportDetail(Long id);

    @Operation(summary = "Historial de acciones de un reporte", description = "RF-071.")
    @ApiResponse(responseCode = "200", description = "Acciones del reporte")
    ResponseEntity<List<ReportActionResponse>> getReportActions(Long id);

    @Operation(summary = "Reportes recibidos por un usuario", description = "RF-081.")
    @ApiResponse(responseCode = "200", description = "Reportes recibidos")
    ResponseEntity<List<ReportResponse>> getReportsReceived(Long id);

    @Operation(summary = "Reportes enviados por un usuario", description = "RF-081.")
    @ApiResponse(responseCode = "200", description = "Reportes enviados")
    ResponseEntity<List<ReportResponse>> getReportsSent(Long id);
}
