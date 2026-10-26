package com.parosurvivors.serviya.metrics.infrastructure.mappers;

import com.parosurvivors.serviya.metrics.domain.ClientMetrics;
import com.parosurvivors.serviya.metrics.infrastructure.entities.ClientMetricsEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface ClientMetricsPersistenceMapper {

    // activeRequests no se persiste (no existe en la tabla): se calcula por consulta en la capa de servicio.
    @Mapping(target = "activeRequests", ignore = true)
    ClientMetrics toDomain(ClientMetricsEntity entity);

    ClientMetricsEntity toEntity(ClientMetrics domain);
}
