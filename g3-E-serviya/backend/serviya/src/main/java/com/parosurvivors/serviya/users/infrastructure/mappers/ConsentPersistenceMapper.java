package com.parosurvivors.serviya.users.infrastructure.mappers;

import com.parosurvivors.serviya.users.domain.Consent;
import com.parosurvivors.serviya.users.infrastructure.entities.ConsentEntity;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface ConsentPersistenceMapper {

    Consent toDomain(ConsentEntity entity);

    ConsentEntity toEntity(Consent domain);
}
