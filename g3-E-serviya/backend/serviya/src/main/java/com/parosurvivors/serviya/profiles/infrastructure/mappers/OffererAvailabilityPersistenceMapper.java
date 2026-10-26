package com.parosurvivors.serviya.profiles.infrastructure.mappers;

import com.parosurvivors.serviya.profiles.domain.OffererAvailability;
import com.parosurvivors.serviya.profiles.infrastructure.entities.OffererAvailabilityEntity;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface OffererAvailabilityPersistenceMapper {

    OffererAvailability toDomain(OffererAvailabilityEntity entity);

    OffererAvailabilityEntity toEntity(OffererAvailability domain);
}
