package com.parosurvivors.serviya.metrics.infrastructure.mappers;

import com.parosurvivors.serviya.metrics.domain.OffererTagMetrics;
import com.parosurvivors.serviya.metrics.infrastructure.entities.OffererTagMetricsEntity;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface OffererTagMetricsPersistenceMapper {

    OffererTagMetrics toDomain(OffererTagMetricsEntity entity);

    OffererTagMetricsEntity toEntity(OffererTagMetrics domain);
}
