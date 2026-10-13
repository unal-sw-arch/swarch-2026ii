package com.parosurvivors.serviya.metrics.infrastructure.mappers;

import com.parosurvivors.serviya.metrics.domain.OffererMetrics;
import com.parosurvivors.serviya.metrics.infrastructure.entities.OffererMetricsEntity;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface OffererMetricsPersistenceMapper {

    OffererMetrics toDomain(OffererMetricsEntity entity);

    OffererMetricsEntity toEntity(OffererMetrics domain);
}
