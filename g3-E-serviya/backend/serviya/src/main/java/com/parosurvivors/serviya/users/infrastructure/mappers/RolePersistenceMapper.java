package com.parosurvivors.serviya.users.infrastructure.mappers;

import com.parosurvivors.serviya.users.domain.Role;
import com.parosurvivors.serviya.users.infrastructure.entities.RoleEntity;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface RolePersistenceMapper {

    Role toDomain(RoleEntity entity);

    RoleEntity toEntity(Role domain);
}
