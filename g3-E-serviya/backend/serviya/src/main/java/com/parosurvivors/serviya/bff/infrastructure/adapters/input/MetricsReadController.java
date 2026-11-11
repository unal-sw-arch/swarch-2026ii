package com.parosurvivors.serviya.bff.infrastructure.adapters.input;

import com.parosurvivors.serviya.bff.application.services.MetricsReadQueryService;
import com.parosurvivors.serviya.bff.infrastructure.dto.response.ClientMetricsResponse;
import com.parosurvivors.serviya.bff.infrastructure.dto.response.ClientTagMetricsResponse;
import com.parosurvivors.serviya.bff.infrastructure.dto.response.OffererTagMetricsResponse;
import com.parosurvivors.serviya.bff.infrastructure.dto.response.UserMetricsResponse;
import com.parosurvivors.serviya.bff.infrastructure.mappers.BffWebMapper;
import com.parosurvivors.serviya.shared.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Composicion HTTP de las metricas que necesitan tags o solicitudes activas.
 */
@RestController
@RequiredArgsConstructor
@Tag(name = "Metricas compuestas", description = "Metricas enriquecidas por el BFF")
public class MetricsReadController {

    private final MetricsReadQueryService metricsReadQueryService;
    private final BffWebMapper mapper;

    @Operation(summary = "Conteo de tags de un oferente")
    @GetMapping("/api/v1/offerers/{id}/tag-metrics")
    public ResponseEntity<List<OffererTagMetricsResponse>> getOffererTagMetrics(@PathVariable Long id) {
        return ResponseEntity.ok(metricsReadQueryService.getOffererTagMetrics(id).stream()
                .map(mapper::toResponse)
                .toList());
    }

    @Operation(summary = "Conteo de tags de un cliente")
    @GetMapping("/api/v1/clients/{id}/tag-metrics")
    public ResponseEntity<List<ClientTagMetricsResponse>> getClientTagMetrics(@PathVariable Long id) {
        return ResponseEntity.ok(metricsReadQueryService.getClientTagMetrics(id).stream()
                .map(mapper::toResponse)
                .toList());
    }

    @Operation(summary = "Todas las metricas de un cliente, con solicitudes activas")
    @GetMapping("/api/v1/clients/{id}/metrics")
    public ResponseEntity<ClientMetricsResponse> getClientMetrics(@PathVariable Long id) {
        return ResponseEntity.ok(mapper.toResponse(metricsReadQueryService.getClientMetrics(id)));
    }

    @Operation(summary = "Metricas propias segun los roles del usuario")
    @GetMapping("/api/v1/users/me/metrics")
    public ResponseEntity<UserMetricsResponse> getOwnMetrics() {
        return ResponseEntity.ok(mapper.toResponse(
                metricsReadQueryService.getOwnMetrics(CurrentUser.id())));
    }
}
