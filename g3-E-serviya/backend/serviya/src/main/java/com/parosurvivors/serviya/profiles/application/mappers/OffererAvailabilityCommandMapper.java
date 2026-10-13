package com.parosurvivors.serviya.profiles.application.mappers;

import com.parosurvivors.serviya.profiles.application.dto.command.SetAvailabilitySlotCommand;
import com.parosurvivors.serviya.profiles.domain.OffererAvailability;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

/**
 * Mapper de aplicacion (MapStruct) Command -> dominio de la disponibilidad del oferente. Capa de aplicacion.
 * PLACEHOLDER: convierte las franjas del reemplazo masivo (setSchedule). El offererId lo fija el servicio.
 */
@Mapper(componentModel = "spring")
public interface OffererAvailabilityCommandMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "offererId", ignore = true)
    OffererAvailability toDomain(SetAvailabilitySlotCommand command);

    List<OffererAvailability> toDomain(List<SetAvailabilitySlotCommand> commands);
}
