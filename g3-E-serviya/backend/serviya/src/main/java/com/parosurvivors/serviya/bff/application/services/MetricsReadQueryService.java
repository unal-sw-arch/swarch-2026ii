package com.parosurvivors.serviya.bff.application.services;

import com.parosurvivors.serviya.bff.application.dto.ClientMetricsView;
import com.parosurvivors.serviya.bff.application.dto.ClientTagMetricView;
import com.parosurvivors.serviya.bff.application.dto.OffererMetricsView;
import com.parosurvivors.serviya.bff.application.dto.OffererTagMetricView;
import com.parosurvivors.serviya.bff.application.dto.UserMetricsView;
import com.parosurvivors.serviya.feedback.api.FeedbackQueryApi;
import com.parosurvivors.serviya.feedback.api.dto.TagCatalogEntry;
import com.parosurvivors.serviya.metrics.api.MetricsQueryApi;
import com.parosurvivors.serviya.metrics.api.dto.ClientMetricsSnapshot;
import com.parosurvivors.serviya.metrics.api.dto.OffererMetricsSnapshot;
import com.parosurvivors.serviya.metrics.api.dto.TagMetricSnapshot;
import com.parosurvivors.serviya.requests.api.RequestQueryApi;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Composicion de las lecturas de metricas: enriquece tags con feedback y anade el conteo de
 * solicitudes activas desde requests. metrics queda como proveedor de sus propios contadores.
 */
@Component
@RequiredArgsConstructor
public class MetricsReadQueryService {

    private final MetricsQueryApi metricsQueryApi;
    private final FeedbackQueryApi feedbackQueryApi;
    private final RequestQueryApi requestQueryApi;

    public List<OffererTagMetricView> getOffererTagMetrics(Long offererId) {
        Map<Long, TagCatalogEntry> catalog = catalog(feedbackQueryApi.getServiceTags());
        return metricsQueryApi.getOffererTagMetrics(offererId).stream()
                .map(metric -> new OffererTagMetricView(
                        offererId,
                        metric.tagId(),
                        tagName(catalog, metric),
                        isPositive(catalog, metric),
                        metric.count()))
                .toList();
    }

    public List<ClientTagMetricView> getClientTagMetrics(Long clientId) {
        Map<Long, TagCatalogEntry> catalog = catalog(feedbackQueryApi.getClientTags());
        return metricsQueryApi.getClientTagMetrics(clientId).stream()
                .map(metric -> new ClientTagMetricView(
                        clientId,
                        metric.tagId(),
                        tagName(catalog, metric),
                        isPositive(catalog, metric),
                        metric.count()))
                .toList();
    }

    public ClientMetricsView getClientMetrics(Long clientId) {
        ClientMetricsSnapshot metrics = metricsQueryApi.getClientMetrics(clientId);
        int activeRequests = (int) requestQueryApi.countActiveClientRequests(clientId);
        return toClientView(metrics, activeRequests);
    }

    public UserMetricsView getOwnMetrics(Long userId) {
        OffererMetricsSnapshot offerer = metricsQueryApi.getOffererMainMetrics(userId);
        ClientMetricsSnapshot client = metricsQueryApi.getClientMainMetrics(userId);
        int activeRequests = (int) requestQueryApi.countActiveClientRequests(userId);
        return new UserMetricsView(toOfferView(offerer), toClientView(client, activeRequests));
    }

    private OffererMetricsView toOfferView(OffererMetricsSnapshot metrics) {
        return new OffererMetricsView(
                metrics.offererId(),
                metrics.averageRating(),
                metrics.totalRatings(),
                metrics.totalComments(),
                metrics.totalPositiveTags(),
                metrics.totalNegativeTags(),
                metrics.totalRequestsReceived(),
                metrics.totalAcceptedRequests(),
                metrics.totalCompletedServices(),
                metrics.totalCancelledServices(),
                metrics.totalRescheduleProposalsSent(),
                metrics.totalNotProvidedServices(),
                metrics.updatedAt());
    }

    private ClientMetricsView toClientView(ClientMetricsSnapshot metrics, int activeRequests) {
        return new ClientMetricsView(
                metrics.clientId(),
                metrics.averageRating(),
                metrics.totalRatings(),
                metrics.totalComments(),
                metrics.totalPositiveTags(),
                metrics.totalNegativeTags(),
                metrics.totalRequestsSent(),
                metrics.totalAcceptedRequests(),
                metrics.totalCompletedRequests(),
                metrics.totalCancelledRequests(),
                metrics.totalRescheduledRequests(),
                metrics.totalNotProvidedRequests(),
                activeRequests,
                metrics.updatedAt());
    }

    private Map<Long, TagCatalogEntry> catalog(List<TagCatalogEntry> tags) {
        return tags.stream().collect(Collectors.toMap(TagCatalogEntry::id, Function.identity()));
    }

    private String tagName(Map<Long, TagCatalogEntry> catalog, TagMetricSnapshot metric) {
        TagCatalogEntry tag = catalog.get(metric.tagId());
        return tag != null ? tag.name() : "Tag #" + metric.tagId();
    }

    private boolean isPositive(Map<Long, TagCatalogEntry> catalog, TagMetricSnapshot metric) {
        TagCatalogEntry tag = catalog.get(metric.tagId());
        return tag != null && tag.positive();
    }
}
