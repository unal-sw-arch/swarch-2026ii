package com.parosurvivors.serviya.metrics.infrastructure.adapters.input;

import com.parosurvivors.serviya.metrics.application.ports.input.ClientMetricsServicePort;
import com.parosurvivors.serviya.metrics.application.ports.input.OffererMetricsServicePort;
import com.parosurvivors.serviya.metrics.application.ports.input.ServiceMetricsServicePort;
import com.parosurvivors.serviya.metrics.application.ports.input.ServiceTagMetricsServicePort;
import com.parosurvivors.serviya.metrics.infrastructure.adapters.input.api.MetricsApi;
import com.parosurvivors.serviya.metrics.infrastructure.dto.response.ClientMetricsSummaryResponse;
import com.parosurvivors.serviya.metrics.infrastructure.dto.response.OffererMetricsResponse;
import com.parosurvivors.serviya.metrics.infrastructure.dto.response.OffererMetricsSummaryResponse;
import com.parosurvivors.serviya.metrics.infrastructure.dto.response.ServiceMetricsResponse;
import com.parosurvivors.serviya.metrics.infrastructure.dto.response.ServiceTagMetricsResponse;
import com.parosurvivors.serviya.metrics.infrastructure.mappers.MetricsWebMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Adaptador de entrada (REST) de metricas. Placeholder funcional; documentacion en {@link MetricsApi}.
 * Mapea el dominio de metricas a Response via {@link MetricsWebMapper}.
 */
@RestController
@RequiredArgsConstructor
public class MetricsController implements MetricsApi {

    private final ServiceMetricsServicePort serviceMetricsService;
    private final ServiceTagMetricsServicePort serviceTagMetricsService;
    private final OffererMetricsServicePort offererMetricsService;
    private final ClientMetricsServicePort clientMetricsService;
    private final MetricsWebMapper mapper;

    @Override
    @GetMapping("/api/v1/services/{id}/metrics")
    public ResponseEntity<ServiceMetricsResponse> getServiceMetrics(@PathVariable Long id) {
        return ResponseEntity.ok(mapper.toResponse(serviceMetricsService.getMetrics(id)));
    }

    @Override
    @GetMapping("/api/v1/services/{id}/tag-metrics")
    public ResponseEntity<List<ServiceTagMetricsResponse>> getServiceTagMetrics(@PathVariable Long id) {
        return ResponseEntity.ok(mapper.toServiceTagResponses(serviceTagMetricsService.getTagMetrics(id)));
    }

    @Override
    @GetMapping("/api/v1/offerers/{id}/metrics")
    public ResponseEntity<OffererMetricsResponse> getOffererMetrics(@PathVariable Long id) {
        return ResponseEntity.ok(mapper.toResponse(offererMetricsService.getAllMetrics(id)));
    }

    @Override
    @GetMapping("/api/v1/offerers/{id}/metrics/main")
    public ResponseEntity<OffererMetricsSummaryResponse> getOffererMainMetrics(@PathVariable Long id) {
        return ResponseEntity.ok(mapper.toSummaryResponse(offererMetricsService.getMainMetrics(id)));
    }

    @Override
    @GetMapping("/api/v1/clients/{id}/metrics/main")
    public ResponseEntity<ClientMetricsSummaryResponse> getClientMainMetrics(@PathVariable Long id) {
        return ResponseEntity.ok(mapper.toSummaryResponse(clientMetricsService.getMainMetrics(id)));
    }
}
