package com.parosurvivors.serviya.profiles.application.mappers;

import com.parosurvivors.serviya.profiles.application.dto.command.UpdateOffererProfileCommand;
import com.parosurvivors.serviya.profiles.domain.OffererProfile;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

/**
 * Mapper de aplicacion (MapStruct) Command -> dominio del perfil de oferente. Capa de aplicacion.
 * PLACEHOLDER: aplica el PATCH del perfil de oferente (solo campos no-nulos).
 */
@Mapper(componentModel = "spring")
public interface OffererProfileCommandMapper {

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "userId", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    void updateFromCommand(UpdateOffererProfileCommand command, @MappingTarget OffererProfile profile);
}
