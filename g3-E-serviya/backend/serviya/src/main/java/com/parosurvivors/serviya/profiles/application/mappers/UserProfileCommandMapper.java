package com.parosurvivors.serviya.profiles.application.mappers;

import com.parosurvivors.serviya.profiles.application.dto.command.UpdateProfileCommand;
import com.parosurvivors.serviya.profiles.domain.UserProfile;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

/**
 * Mapper de aplicacion (MapStruct) Command -> dominio del perfil personal. Capa de aplicacion.
 * PLACEHOLDER: aplica el PATCH del perfil (solo campos no-nulos). Ojo con los nombres distintos
 * (phone->phoneNumber, photoUrl->profilePhotoUrl, description->bio).
 */
@Mapper(componentModel = "spring")
public interface UserProfileCommandMapper {

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "userId", ignore = true)
    @Mapping(target = "documentType", ignore = true)
    @Mapping(target = "documentNumber", ignore = true)
    @Mapping(target = "primaryAddressId", ignore = true)
    @Mapping(target = "profileType", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "phoneNumber", source = "phone")
    @Mapping(target = "profilePhotoUrl", source = "photoUrl")
    @Mapping(target = "bio", source = "description")
    void updateFromCommand(UpdateProfileCommand command, @MappingTarget UserProfile profile);
}
