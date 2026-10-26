package com.parosurvivors.serviya.profiles.infrastructure.mappers;

import com.parosurvivors.serviya.profiles.application.dto.command.SetAvailabilitySlotCommand;
import com.parosurvivors.serviya.profiles.domain.OffererAvailability;
import com.parosurvivors.serviya.profiles.infrastructure.dto.form.AvailabilitySlotForm;
import com.parosurvivors.serviya.profiles.infrastructure.dto.response.AvailabilitySlotResponse;
import org.mapstruct.Mapper;

import java.util.List;

/**
 * Mapper web (MapStruct) de la disponibilidad general del oferente: Form->Command y dominio->Response.
 * TODO: completar mapeos donde difieran los nombres de campo.
 */
@Mapper(componentModel = "spring")
public interface OffererAvailabilityWebMapper {

    SetAvailabilitySlotCommand toCommand(AvailabilitySlotForm form);

    List<SetAvailabilitySlotCommand> toCommands(List<AvailabilitySlotForm> forms);

    AvailabilitySlotResponse toResponse(OffererAvailability availability);

    List<AvailabilitySlotResponse> toResponses(List<OffererAvailability> availabilities);
}
