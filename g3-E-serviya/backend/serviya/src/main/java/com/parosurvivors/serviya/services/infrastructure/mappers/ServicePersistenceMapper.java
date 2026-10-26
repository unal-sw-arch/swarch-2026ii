package com.parosurvivors.serviya.services.infrastructure.mappers;

import com.parosurvivors.serviya.services.domain.Service;
import com.parosurvivors.serviya.services.infrastructure.entities.ServiceEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface ServicePersistenceMapper {

    ServiceEntity toEntity(Service domain);

    Service toDomain(ServiceEntity entity);
}
