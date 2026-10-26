package com.parosurvivors.serviya.users.infrastructure.mappers;

import com.parosurvivors.serviya.users.domain.PasswordResetToken;
import com.parosurvivors.serviya.users.infrastructure.entities.PasswordResetTokenEntity;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface PasswordResetTokenPersistenceMapper {

    PasswordResetToken toDomain(PasswordResetTokenEntity entity);

    PasswordResetTokenEntity toEntity(PasswordResetToken domain);
}
