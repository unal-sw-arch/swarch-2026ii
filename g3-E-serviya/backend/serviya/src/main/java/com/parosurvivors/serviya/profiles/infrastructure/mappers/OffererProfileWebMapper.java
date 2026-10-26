package com.parosurvivors.serviya.profiles.infrastructure.mappers;

import com.parosurvivors.serviya.profiles.application.dto.command.UpdateOffererProfileCommand;
import com.parosurvivors.serviya.profiles.application.dto.result.OffererPublicProfileResult;
import com.parosurvivors.serviya.profiles.infrastructure.dto.response.OffererPublicProfileDetailResponse;
import com.parosurvivors.serviya.profiles.domain.OffererProfile;
import com.parosurvivors.serviya.profiles.domain.OffererProfileSummary;
import com.parosurvivors.serviya.profiles.infrastructure.dto.form.UpdateOffererProfileForm;
import com.parosurvivors.serviya.profiles.infrastructure.dto.response.OffererProfileSummaryResponse;
import com.parosurvivors.serviya.profiles.infrastructure.dto.response.OffererPublicProfileResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * Mapper web (MapStruct) del perfil de oferente: Form->Command y dominio/read model->Response.
 * TODO: completar mapeos donde difieran los nombres de campo.
 */
@Mapper(componentModel = "spring")
public interface OffererProfileWebMapper {

    @Mapping(target = "userId", source = "userId")
    UpdateOffererProfileCommand toCommand(UpdateOffererProfileForm form, Long userId);

    OffererPublicProfileResponse toResponse(OffererProfile profile);

    OffererProfileSummaryResponse toResponse(OffererProfileSummary summary);

    /** RF-027: agregado publico (identidad + reputacion + metricas + servicios activos). */
    OffererPublicProfileDetailResponse toResponse(OffererPublicProfileResult result);
}
