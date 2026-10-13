package com.parosurvivors.serviya.users.application.mappers;

import com.parosurvivors.serviya.users.application.dto.command.CreateUserAccountCommand;
import com.parosurvivors.serviya.users.domain.User;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * Mapper de aplicacion (MapStruct) Command -> dominio del feature users. Capa de aplicacion
 * (no depende de infraestructura). PLACEHOLDER: la creacion de usuario es una orquestacion (User +
 * UserProfile + roles + consentimiento), por lo que aqui solo se construye la parte de credenciales del
 * User; el resto lo arma UserCreationService.
 * TODO: el passwordHash NO se mapea directo (hay que cifrar la contrasena); roles y perfil van aparte.
 */
@Mapper(componentModel = "spring")
public interface UserCommandMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "passwordHash", ignore = true) // TODO cifrar command.password()
    @Mapping(target = "banned", ignore = true)
    @Mapping(target = "deletedAt", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "roles", ignore = true) // TODO derivar de command.role()
    User toDomain(CreateUserAccountCommand command);
}
