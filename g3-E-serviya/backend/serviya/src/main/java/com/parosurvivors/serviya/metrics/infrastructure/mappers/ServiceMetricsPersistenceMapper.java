package com.parosurvivors.serviya.metrics.infrastructure.mappers;

import com.parosurvivors.serviya.metrics.domain.ServiceMetrics;
import com.parosurvivors.serviya.metrics.infrastructure.entities.ServiceMetricsEntity;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface ServiceMetricsPersistenceMapper {

    ServiceMetrics toDomain(ServiceMetricsEntity entity);

    ServiceMetricsEntity toEntity(ServiceMetrics domain);
}
