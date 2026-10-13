package com.parosurvivors.serviya.metrics.infrastructure.mappers;

import com.parosurvivors.serviya.metrics.domain.ClientMetrics;
import com.parosurvivors.serviya.metrics.domain.ClientTagMetrics;
import com.parosurvivors.serviya.metrics.domain.OffererMetrics;
import com.parosurvivors.serviya.metrics.domain.OffererTagMetrics;
import com.parosurvivors.serviya.metrics.domain.ServiceMetrics;
import com.parosurvivors.serviya.metrics.domain.ServiceTagMetrics;
import com.parosurvivors.serviya.metrics.infrastructure.dto.response.ClientMetricsResponse;
import com.parosurvivors.serviya.metrics.infrastructure.dto.response.ClientMetricsSummaryResponse;
import com.parosurvivors.serviya.metrics.infrastructure.dto.response.ClientTagMetricsResponse;
import com.parosurvivors.serviya.metrics.infrastructure.dto.response.OffererMetricsResponse;
import com.parosurvivors.serviya.metrics.infrastructure.dto.response.OffererMetricsSummaryResponse;
import com.parosurvivors.serviya.metrics.infrastructure.dto.response.OffererTagMetricsResponse;
import com.parosurvivors.serviya.metrics.infrastructure.dto.response.ServiceMetricsResponse;
import com.parosurvivors.serviya.metrics.infrastructure.dto.response.ServiceTagMetricsResponse;
import com.parosurvivors.serviya.metrics.infrastructure.dto.response.UserMetricsResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

/**
 * Mapper web (MapStruct) de metricas: dominio->Response (solo lecturas).
 * TODO: completar mapeos donde difieran los nombres de campo.
 */
@Mapper(componentModel = "spring")
public interface MetricsWebMapper {

    ServiceMetricsResponse toResponse(ServiceMetrics metrics);

    OffererMetricsResponse toResponse(OffererMetrics metrics);

    OffererMetricsSummaryResponse toSummaryResponse(OffererMetrics metrics);

    ClientMetricsResponse toResponse(ClientMetrics metrics);

    ClientMetricsSummaryResponse toSummaryResponse(ClientMetrics metrics);

    ServiceTagMetricsResponse toResponse(ServiceTagMetrics metrics);

    @Mapping(target = "tagName", ignore = true)
    @Mapping(target = "positive", ignore = true)
    OffererTagMetricsResponse toResponse(OffererTagMetrics metrics);

    @Mapping(target = "tagName", ignore = true)
    @Mapping(target = "positive", ignore = true)
    ClientTagMetricsResponse toResponse(ClientTagMetrics metrics);

    List<ServiceTagMetricsResponse> toServiceTagResponses(List<ServiceTagMetrics> metrics);

    List<OffererTagMetricsResponse> toOffererTagResponses(List<OffererTagMetrics> metrics);

    List<ClientTagMetricsResponse> toClientTagResponses(List<ClientTagMetrics> metrics);

    @Mapping(target = "offererMetrics", source = "offererMetrics")
    @Mapping(target = "clientMetrics", source = "clientMetrics")
    UserMetricsResponse toUserMetricsResponse(OffererMetrics offererMetrics, ClientMetrics clientMetrics);
}
