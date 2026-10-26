package com.parosurvivors.serviya.metrics.infrastructure.adapters.input;

import com.parosurvivors.serviya.metrics.application.ports.input.ClientMetricsServicePort;
import com.parosurvivors.serviya.metrics.application.ports.input.ClientTagMetricsServicePort;
import com.parosurvivors.serviya.metrics.application.ports.input.OffererMetricsServicePort;
import com.parosurvivors.serviya.metrics.application.ports.input.OffererTagMetricsServicePort;
import com.parosurvivors.serviya.metrics.application.ports.input.ServiceMetricsServicePort;
import com.parosurvivors.serviya.metrics.application.ports.input.ServiceTagMetricsServicePort;
import com.parosurvivors.serviya.metrics.infrastructure.adapters.input.api.MetricsApi;
import com.parosurvivors.serviya.metrics.infrastructure.dto.response.ClientMetricsResponse;
import com.parosurvivors.serviya.metrics.infrastructure.dto.response.ClientMetricsSummaryResponse;
import com.parosurvivors.serviya.metrics.infrastructure.dto.response.ClientTagMetricsResponse;
import com.parosurvivors.serviya.metrics.infrastructure.dto.response.OffererMetricsResponse;
import com.parosurvivors.serviya.metrics.infrastructure.dto.response.OffererMetricsSummaryResponse;
import com.parosurvivors.serviya.metrics.infrastructure.dto.response.OffererTagMetricsResponse;
import com.parosurvivors.serviya.metrics.infrastructure.dto.response.ServiceMetricsResponse;
import com.parosurvivors.serviya.metrics.infrastructure.dto.response.ServiceTagMetricsResponse;
import com.parosurvivors.serviya.metrics.infrastructure.dto.response.UserMetricsResponse;
import com.parosurvivors.serviya.metrics.infrastructure.mappers.MetricsWebMapper;
import com.parosurvivors.serviya.feedback.application.ports.input.ClientFeedbackTagCatalogServicePort;
import com.parosurvivors.serviya.feedback.application.ports.input.ServiceFeedbackTagCatalogServicePort;
import com.parosurvivors.serviya.feedback.domain.ClientFeedbackTagCatalog;
import com.parosurvivors.serviya.feedback.domain.ServiceFeedbackTagCatalog;
import com.parosurvivors.serviya.shared.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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
    private final OffererTagMetricsServicePort offererTagMetricsService;
    private final ClientMetricsServicePort clientMetricsService;
    private final ClientTagMetricsServicePort clientTagMetricsService;
    private final ClientFeedbackTagCatalogServicePort clientFeedbackTagCatalogServicePort;
    private final ServiceFeedbackTagCatalogServicePort serviceFeedbackTagCatalogServicePort;
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
    @GetMapping("/api/v1/offerers/{id}/tag-metrics")
    public ResponseEntity<List<OffererTagMetricsResponse>> getOffererTagMetrics(@PathVariable Long id) {
        Map<Long, ServiceFeedbackTagCatalog> catalog = serviceFeedbackTagCatalogServicePort.getCatalog()
                .stream().collect(Collectors.toMap(ServiceFeedbackTagCatalog::getId, t -> t));

        List<OffererTagMetricsResponse> enriched = offererTagMetricsService.getTagMetrics(id)
                .stream().map(tm -> {
                    ServiceFeedbackTagCatalog tag = catalog.get(tm.getTagId());
                    String tagName = tag != null ? tag.getTagName() : "Tag #" + tm.getTagId();
                    boolean positive = tag != null && tag.isPositive();
                    return new OffererTagMetricsResponse(tm.getOffererId(), tm.getTagId(), tagName, positive, tm.getTagCount());
                }).toList();
        return ResponseEntity.ok(enriched);
    }

    @Override
    @GetMapping("/api/v1/clients/{id}/metrics")
    public ResponseEntity<ClientMetricsResponse> getClientMetrics(@PathVariable Long id) {
        return ResponseEntity.ok(mapper.toResponse(clientMetricsService.getAllMetrics(id)));
    }

    @Override
    @GetMapping("/api/v1/clients/{id}/metrics/main")
    public ResponseEntity<ClientMetricsSummaryResponse> getClientMainMetrics(@PathVariable Long id) {
        return ResponseEntity.ok(mapper.toSummaryResponse(clientMetricsService.getMainMetrics(id)));
    }

    @Override
    @GetMapping("/api/v1/clients/{id}/tag-metrics")
    public ResponseEntity<List<ClientTagMetricsResponse>> getClientTagMetrics(@PathVariable Long id) {
        Map<Long, ClientFeedbackTagCatalog> catalog = clientFeedbackTagCatalogServicePort.getCatalog()
                .stream().collect(Collectors.toMap(ClientFeedbackTagCatalog::getId, t -> t));

        List<ClientTagMetricsResponse> enriched = clientTagMetricsService.getTagMetrics(id)
                .stream().map(tm -> {
                    ClientFeedbackTagCatalog tag = catalog.get(tm.getTagId());
                    String tagName = tag != null ? tag.getTagName() : "Tag #" + tm.getTagId();
                    boolean positive = tag != null && tag.isPositive();
                    return new ClientTagMetricsResponse(tm.getClientId(), tm.getTagId(), tagName, positive, tm.getTagCount());
                }).toList();
        return ResponseEntity.ok(enriched);
    }

    @Override
    @GetMapping("/api/v1/users/me/metrics")
    public ResponseEntity<UserMetricsResponse> getOwnMetrics() {
        Long userId = currentUserId();
        return ResponseEntity.ok(mapper.toUserMetricsResponse(
                offererMetricsService.getMainMetrics(userId),
                clientMetricsService.getMainMetrics(userId)));
    }

    /** Id del usuario autenticado (extraido del JWT por CurrentUser). */
    private Long currentUserId() {
        return CurrentUser.id();
    }
}
