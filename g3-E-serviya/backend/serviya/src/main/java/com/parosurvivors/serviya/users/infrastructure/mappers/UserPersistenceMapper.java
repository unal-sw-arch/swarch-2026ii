package com.parosurvivors.serviya.users.infrastructure.mappers;

import com.parosurvivors.serviya.users.domain.User;
import com.parosurvivors.serviya.users.infrastructure.entities.UserEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * Conversión dominio &lt;-&gt; entidad JPA para {@link User}. Vive en la capa de
 * infraestructura porque referencia {@link UserEntity} (regla de dependencias hexagonal).
 * Los roles se hidratan aparte (tabla user_roles), por eso se ignoran aquí.
 */
@Mapper(componentModel = "spring")
public interface UserPersistenceMapper {

    @Mapping(target = "roles", ignore = true)
    User toDomain(UserEntity entity);

    UserEntity toEntity(User domain);
}
