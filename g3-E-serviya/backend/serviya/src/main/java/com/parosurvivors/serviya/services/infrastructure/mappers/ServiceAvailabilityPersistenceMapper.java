package com.parosurvivors.serviya.services.infrastructure.mappers;

import com.parosurvivors.serviya.services.domain.ServiceAvailability;
import com.parosurvivors.serviya.services.infrastructure.entities.ServiceAvailabilityEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface ServiceAvailabilityPersistenceMapper {

    ServiceAvailabilityEntity toEntity(ServiceAvailability domain);

    @Mapping(target = "isActive", source = "active")
    ServiceAvailability toDomain(ServiceAvailabilityEntity entity);
}
