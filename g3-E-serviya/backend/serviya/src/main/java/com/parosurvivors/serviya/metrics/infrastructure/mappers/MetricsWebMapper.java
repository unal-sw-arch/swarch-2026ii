package com.parosurvivors.serviya.metrics.infrastructure.mappers;

import com.parosurvivors.serviya.metrics.domain.ClientMetrics;
import com.parosurvivors.serviya.metrics.domain.OffererMetrics;
import com.parosurvivors.serviya.metrics.domain.ServiceMetrics;
import com.parosurvivors.serviya.metrics.domain.ServiceTagMetrics;
import com.parosurvivors.serviya.metrics.infrastructure.dto.response.ClientMetricsSummaryResponse;
import com.parosurvivors.serviya.metrics.infrastructure.dto.response.OffererMetricsResponse;
import com.parosurvivors.serviya.metrics.infrastructure.dto.response.OffererMetricsSummaryResponse;
import com.parosurvivors.serviya.metrics.infrastructure.dto.response.ServiceMetricsResponse;
import com.parosurvivors.serviya.metrics.infrastructure.dto.response.ServiceTagMetricsResponse;
import org.mapstruct.Mapper;

import java.util.List;

/**
 * Mapper web (MapStruct) de metricas propias del modulo. Las vistas compuestas (tags con nombre,
 * solicitudes activas, metricas propias) las arma el BFF.
 */
@Mapper(componentModel = "spring")
public interface MetricsWebMapper {

    ServiceMetricsResponse toResponse(ServiceMetrics metrics);

    OffererMetricsResponse toResponse(OffererMetrics metrics);

    OffererMetricsSummaryResponse toSummaryResponse(OffererMetrics metrics);

    ClientMetricsSummaryResponse toSummaryResponse(ClientMetrics metrics);

    ServiceTagMetricsResponse toResponse(ServiceTagMetrics metrics);

    List<ServiceTagMetricsResponse> toServiceTagResponses(List<ServiceTagMetrics> metrics);
}
