package com.parosurvivors.serviya.feedback.application.mappers;

import com.parosurvivors.serviya.feedback.application.dto.command.SubmitServiceFeedbackCommand;
import com.parosurvivors.serviya.feedback.domain.ServiceFeedback;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * Mapper de aplicacion (MapStruct) Command -> dominio del feedback de servicio. Capa de aplicacion.
 * Un solo command produce una unica entidad ServiceFeedback (rating + reseña + tags unificados);
 * rating y comment pueden venir null. id/createdAt los asigna la persistencia.
 */
@Mapper(componentModel = "spring")
public interface ServiceFeedbackCommandMapper {

    // serviceId se deriva de la solicitud (service_requests.service_id) en el servicio al enviar
    // el feedback; no viene en el command, por eso se ignora aquí.
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "serviceId", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    ServiceFeedback toFeedback(SubmitServiceFeedbackCommand command);
}
