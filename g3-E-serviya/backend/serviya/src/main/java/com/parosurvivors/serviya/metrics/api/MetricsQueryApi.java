package com.parosurvivors.serviya.metrics.api;

import com.parosurvivors.serviya.metrics.api.dto.ClientMetricsSnapshot;
import com.parosurvivors.serviya.metrics.api.dto.OffererMetricsSnapshot;
import com.parosurvivors.serviya.metrics.api.dto.ServiceMetricsSnapshot;
import com.parosurvivors.serviya.metrics.api.dto.TagMetricSnapshot;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Consultas publicas del modulo metrics.
 */
public interface MetricsQueryApi {

    ServiceMetricsSnapshot getServiceMetrics(Long serviceId);
    Map<Long, ServiceMetricsSnapshot> getServiceMetrics(Collection<Long> serviceIds);

    /** Ids de servicios cuyo averageRating cae en el rango (null = sin limite en ese extremo). */
    List<Long> findServiceIdsByAverageRating(Double minRating, Double maxRating);

    OffererMetricsSnapshot getOffererMetrics(Long offererId);

    OffererMetricsSnapshot getOffererMainMetrics(Long offererId);

    ClientMetricsSnapshot getClientMetrics(Long clientId);

    ClientMetricsSnapshot getClientMainMetrics(Long clientId);

    List<TagMetricSnapshot> getServiceTagMetrics(Long serviceId);

    List<TagMetricSnapshot> getOffererTagMetrics(Long offererId);

    List<TagMetricSnapshot> getClientTagMetrics(Long clientId);
}
