package com.parosurvivors.serviya.metrics.application.api;

import com.parosurvivors.serviya.metrics.api.MetricsQueryApi;
import com.parosurvivors.serviya.metrics.api.dto.ClientMetricsSnapshot;
import com.parosurvivors.serviya.metrics.api.dto.OffererMetricsSnapshot;
import com.parosurvivors.serviya.metrics.api.dto.ServiceMetricsSnapshot;
import com.parosurvivors.serviya.metrics.api.dto.TagMetricSnapshot;
import com.parosurvivors.serviya.metrics.application.ports.input.ClientMetricsServicePort;
import com.parosurvivors.serviya.metrics.application.ports.input.ClientTagMetricsServicePort;
import com.parosurvivors.serviya.metrics.application.ports.input.OffererMetricsServicePort;
import com.parosurvivors.serviya.metrics.application.ports.input.OffererTagMetricsServicePort;
import com.parosurvivors.serviya.metrics.application.ports.input.ServiceMetricsServicePort;
import com.parosurvivors.serviya.metrics.application.ports.input.ServiceTagMetricsServicePort;
import com.parosurvivors.serviya.metrics.application.ports.output.ServiceMetricsPersistencePort;
import com.parosurvivors.serviya.metrics.domain.ClientMetrics;
import com.parosurvivors.serviya.metrics.domain.ClientTagMetrics;
import com.parosurvivors.serviya.metrics.domain.OffererMetrics;
import com.parosurvivors.serviya.metrics.domain.OffererTagMetrics;
import com.parosurvivors.serviya.metrics.domain.ServiceMetrics;
import com.parosurvivors.serviya.metrics.domain.ServiceTagMetrics;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Implementacion de los contratos publicos de metrics sobre los casos de uso existentes.
 *
 * <p>Los tag metrics solo exponen id + conteo; el nombre/sentimiento se enriquece fuera del modulo
 * con {@code feedback.api}.</p>
 */
@Component
@RequiredArgsConstructor
public class MetricsApiFacade implements MetricsQueryApi {

    private final ServiceMetricsServicePort serviceMetricsServicePort;
    private final ServiceTagMetricsServicePort serviceTagMetricsServicePort;
    private final OffererMetricsServicePort offererMetricsServicePort;
    private final OffererTagMetricsServicePort offererTagMetricsServicePort;
    private final ClientMetricsServicePort clientMetricsServicePort;
    private final ClientTagMetricsServicePort clientTagMetricsServicePort;
    private final ServiceMetricsPersistencePort serviceMetricsPersistencePort;

    @Override
    public ServiceMetricsSnapshot getServiceMetrics(Long serviceId) {
        return toServiceSnapshot(serviceMetricsServicePort.getMetrics(serviceId));
    }

    @Override
    public Map<Long, ServiceMetricsSnapshot> getServiceMetrics(Collection<Long> serviceIds) {
        return serviceMetricsServicePort.getMetricsByServiceIds(serviceIds).entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, entry -> toServiceSnapshot(entry.getValue())));
    }

    @Override
    public List<Long> findServiceIdsByAverageRating(Double minRating, Double maxRating) {
        BigDecimal min = minRating == null ? null : BigDecimal.valueOf(minRating);
        BigDecimal max = maxRating == null ? null : BigDecimal.valueOf(maxRating);
        return serviceMetricsPersistencePort.findServiceIdsByAverageRating(min, max);
    }

    @Override
    public OffererMetricsSnapshot getOffererMetrics(Long offererId) {
        return toOffererSnapshot(offererMetricsServicePort.getAllMetrics(offererId));
    }

    @Override
    public OffererMetricsSnapshot getOffererMainMetrics(Long offererId) {
        return toOffererSnapshot(offererMetricsServicePort.getMainMetrics(offererId));
    }

    @Override
    public ClientMetricsSnapshot getClientMetrics(Long clientId) {
        return toClientSnapshot(clientMetricsServicePort.getAllMetrics(clientId));
    }

    @Override
    public ClientMetricsSnapshot getClientMainMetrics(Long clientId) {
        return toClientSnapshot(clientMetricsServicePort.getMainMetrics(clientId));
    }

    @Override
    public List<TagMetricSnapshot> getServiceTagMetrics(Long serviceId) {
        return serviceTagMetricsServicePort.getTagMetrics(serviceId).stream()
                .map(this::toTagSnapshot)
                .toList();
    }

    @Override
    public List<TagMetricSnapshot> getOffererTagMetrics(Long offererId) {
        return offererTagMetricsServicePort.getTagMetrics(offererId).stream()
                .map(this::toTagSnapshot)
                .toList();
    }

    @Override
    public List<TagMetricSnapshot> getClientTagMetrics(Long clientId) {
        return clientTagMetricsServicePort.getTagMetrics(clientId).stream()
                .map(this::toTagSnapshot)
                .toList();
    }

    private ServiceMetricsSnapshot toServiceSnapshot(ServiceMetrics metrics) {
        return new ServiceMetricsSnapshot(
                metrics.getServiceId(),
                metrics.getAverageRating(),
                valueOrZero(metrics.getTotalRatings()),
                valueOrZero(metrics.getTotalComments()),
                valueOrZero(metrics.getTotalRequestsReceived()),
                metrics.getUpdatedAt());
    }

    private OffererMetricsSnapshot toOffererSnapshot(OffererMetrics metrics) {
        return new OffererMetricsSnapshot(
                metrics.getOffererId(),
                metrics.getAverageRating(),
                valueOrZero(metrics.getTotalRatings()),
                valueOrZero(metrics.getTotalComments()),
                valueOrZero(metrics.getTotalPositiveTags()),
                valueOrZero(metrics.getTotalNegativeTags()),
                valueOrZero(metrics.getTotalRequestsReceived()),
                valueOrZero(metrics.getTotalAcceptedRequests()),
                valueOrZero(metrics.getTotalCompletedServices()),
                valueOrZero(metrics.getTotalCancelledServices()),
                valueOrZero(metrics.getTotalRescheduleProposalsSent()),
                valueOrZero(metrics.getTotalNotProvidedServices()),
                metrics.getUpdatedAt());
    }

    private ClientMetricsSnapshot toClientSnapshot(ClientMetrics metrics) {
        return new ClientMetricsSnapshot(
                metrics.getClientId(),
                metrics.getAverageRating(),
                valueOrZero(metrics.getTotalRatings()),
                valueOrZero(metrics.getTotalComments()),
                valueOrZero(metrics.getTotalPositiveTags()),
                valueOrZero(metrics.getTotalNegativeTags()),
                valueOrZero(metrics.getTotalRequestsSent()),
                valueOrZero(metrics.getTotalAcceptedRequests()),
                valueOrZero(metrics.getTotalCompletedRequests()),
                valueOrZero(metrics.getTotalCancelledRequests()),
                valueOrZero(metrics.getTotalRescheduledRequests()),
                valueOrZero(metrics.getTotalNotProvidedRequests()),
                metrics.getUpdatedAt());
    }

    private TagMetricSnapshot toTagSnapshot(ServiceTagMetrics metrics) {
        return new TagMetricSnapshot(metrics.getTagId(), valueOrZero(metrics.getTagCount()));
    }

    private TagMetricSnapshot toTagSnapshot(OffererTagMetrics metrics) {
        return new TagMetricSnapshot(metrics.getTagId(), valueOrZero(metrics.getTagCount()));
    }

    private TagMetricSnapshot toTagSnapshot(ClientTagMetrics metrics) {
        return new TagMetricSnapshot(metrics.getTagId(), valueOrZero(metrics.getTagCount()));
    }

    private int valueOrZero(Integer value) {
        return value == null ? 0 : value;
    }
}
