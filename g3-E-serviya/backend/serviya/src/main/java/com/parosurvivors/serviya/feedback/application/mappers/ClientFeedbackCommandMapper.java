package com.parosurvivors.serviya.feedback.application.mappers;

import com.parosurvivors.serviya.feedback.application.dto.command.SubmitClientFeedbackCommand;
import com.parosurvivors.serviya.feedback.domain.ClientFeedback;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * Mapper de aplicacion (MapStruct) Command -> dominio del feedback de cliente. Capa de aplicacion.
 * Un solo command produce una unica entidad ClientFeedback (rating + reseña + tags unificados);
 * rating y comment pueden venir null. id/createdAt los asigna la persistencia.
 */
@Mapper(componentModel = "spring")
public interface ClientFeedbackCommandMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    ClientFeedback toFeedback(SubmitClientFeedbackCommand command);
}
