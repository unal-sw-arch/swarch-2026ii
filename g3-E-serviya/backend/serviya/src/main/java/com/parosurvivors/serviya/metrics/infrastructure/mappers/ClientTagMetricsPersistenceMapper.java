package com.parosurvivors.serviya.metrics.infrastructure.mappers;

import com.parosurvivors.serviya.metrics.domain.ClientTagMetrics;
import com.parosurvivors.serviya.metrics.infrastructure.entities.ClientTagMetricsEntity;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface ClientTagMetricsPersistenceMapper {

    ClientTagMetrics toDomain(ClientTagMetricsEntity entity);

    ClientTagMetricsEntity toEntity(ClientTagMetrics domain);
}
