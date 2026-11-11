package com.parosurvivors.serviya.users.infrastructure.mappers;

import com.parosurvivors.serviya.users.domain.RefreshToken;
import com.parosurvivors.serviya.users.infrastructure.entities.RefreshTokenEntity;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface RefreshTokenPersistenceMapper {

    RefreshToken toDomain(RefreshTokenEntity entity);

    RefreshTokenEntity toEntity(RefreshToken domain);
}
