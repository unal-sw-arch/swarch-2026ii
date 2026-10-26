package com.parosurvivors.serviya.metrics.infrastructure.adapters.input.api;

import com.parosurvivors.serviya.metrics.infrastructure.dto.response.ClientMetricsResponse;
import com.parosurvivors.serviya.metrics.infrastructure.dto.response.ClientMetricsSummaryResponse;
import com.parosurvivors.serviya.metrics.infrastructure.dto.response.ClientTagMetricsResponse;
import com.parosurvivors.serviya.metrics.infrastructure.dto.response.OffererMetricsResponse;
import com.parosurvivors.serviya.metrics.infrastructure.dto.response.OffererMetricsSummaryResponse;
import com.parosurvivors.serviya.metrics.infrastructure.dto.response.OffererTagMetricsResponse;
import com.parosurvivors.serviya.metrics.infrastructure.dto.response.ServiceMetricsResponse;
import com.parosurvivors.serviya.metrics.infrastructure.dto.response.ServiceTagMetricsResponse;
import com.parosurvivors.serviya.metrics.infrastructure.dto.response.UserMetricsResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;

import java.util.List;

/**
 * Documentacion OpenAPI/Swagger de las metricas precalculadas (modulo 6, seccion 14).
 * Ver estructura-endpoints.md. Convencion: docs aqui; binding y @Parameter en el controller.
 */
@Tag(name = "Metricas", description = "Metricas precalculadas de servicios, oferentes y clientes")
public interface MetricsApi {

    @Operation(summary = "Metricas de un servicio", description = "RF-040.")
    @ApiResponse(responseCode = "200", description = "Metricas del servicio")
    ResponseEntity<ServiceMetricsResponse> getServiceMetrics(Long id);

    @Operation(summary = "Conteo de tags de un servicio")
    @ApiResponse(responseCode = "200", description = "Tag-metrics del servicio")
    ResponseEntity<List<ServiceTagMetricsResponse>> getServiceTagMetrics(Long id);

    @Operation(summary = "Todas las metricas de un oferente", description = "RF-042, RF-053.")
    @ApiResponse(responseCode = "200", description = "Metricas del oferente")
    ResponseEntity<OffererMetricsResponse> getOffererMetrics(Long id);

    @Operation(summary = "Metricas principales (resumen) de un oferente", description = "RF-042, RF-053.")
    @ApiResponse(responseCode = "200", description = "Resumen de metricas del oferente")
    ResponseEntity<OffererMetricsSummaryResponse> getOffererMainMetrics(Long id);

    @Operation(summary = "Conteo de tags de un oferente")
    @ApiResponse(responseCode = "200", description = "Tag-metrics del oferente")
    ResponseEntity<List<OffererTagMetricsResponse>> getOffererTagMetrics(Long id);

    @Operation(summary = "Todas las metricas de un cliente", description = "RF-054.")
    @ApiResponse(responseCode = "200", description = "Metricas del cliente")
    ResponseEntity<ClientMetricsResponse> getClientMetrics(Long id);

    @Operation(summary = "Metricas principales (resumen) de un cliente", description = "RF-054.")
    @ApiResponse(responseCode = "200", description = "Resumen de metricas del cliente")
    ResponseEntity<ClientMetricsSummaryResponse> getClientMainMetrics(Long id);

    @Operation(summary = "Conteo de tags de un cliente")
    @ApiResponse(responseCode = "200", description = "Tag-metrics del cliente")
    ResponseEntity<List<ClientTagMetricsResponse>> getClientTagMetrics(Long id);

    @Operation(summary = "Metricas propias segun los roles del usuario",
            description = "Devuelve offererMetrics y/o clientMetrics. RF-051, RF-052.")
    @ApiResponse(responseCode = "200", description = "Metricas del usuario autenticado")
    ResponseEntity<UserMetricsResponse> getOwnMetrics();
}
