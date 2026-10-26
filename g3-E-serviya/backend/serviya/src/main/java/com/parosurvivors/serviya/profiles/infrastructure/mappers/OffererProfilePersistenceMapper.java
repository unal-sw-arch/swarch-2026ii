package com.parosurvivors.serviya.profiles.infrastructure.mappers;

import com.parosurvivors.serviya.profiles.domain.OffererProfile;
import com.parosurvivors.serviya.profiles.infrastructure.entities.OffererProfileEntity;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface OffererProfilePersistenceMapper {

    OffererProfile toDomain(OffererProfileEntity entity);

    OffererProfileEntity toEntity(OffererProfile domain);
}
