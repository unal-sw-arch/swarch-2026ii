package com.parosurvivors.serviya.metrics.infrastructure.mappers;

import com.parosurvivors.serviya.metrics.domain.ServiceTagMetrics;
import com.parosurvivors.serviya.metrics.infrastructure.entities.ServiceTagMetricsEntity;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface ServiceTagMetricsPersistenceMapper {

    ServiceTagMetrics toDomain(ServiceTagMetricsEntity entity);

    ServiceTagMetricsEntity toEntity(ServiceTagMetrics domain);
}
