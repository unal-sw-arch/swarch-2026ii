package com.parosurvivors.serviya.profiles.infrastructure.mappers;

import com.parosurvivors.serviya.profiles.application.dto.command.UpdateProfileCommand;
import com.parosurvivors.serviya.profiles.domain.UserProfile;
import com.parosurvivors.serviya.profiles.infrastructure.dto.form.UpdateProfileForm;
import com.parosurvivors.serviya.profiles.infrastructure.dto.response.UserProfileResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * Mapper web (MapStruct) del perfil personal: Form->Command y dominio->Response.
 * TODO: completar mapeos donde difieran los nombres de campo.
 */
@Mapper(componentModel = "spring")
public interface UserProfileWebMapper {

    @Mapping(target = "userId", source = "userId")
    UpdateProfileCommand toCommand(UpdateProfileForm form, Long userId);

    UserProfileResponse toResponse(UserProfile profile);
}
