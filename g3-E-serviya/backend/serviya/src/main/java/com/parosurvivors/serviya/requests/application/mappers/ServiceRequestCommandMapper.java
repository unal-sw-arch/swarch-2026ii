package com.parosurvivors.serviya.requests.application.mappers;

import com.parosurvivors.serviya.requests.application.dto.command.CreateServiceRequestCommand;
import com.parosurvivors.serviya.requests.domain.ServiceRequest;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * Mapper de aplicacion (MapStruct) Command -> dominio de solicitudes. Capa de aplicacion.
 * PLACEHOLDER: lo usara ServiceRequestCommandService.createRequest. El offererId, el precio y el
 * estado inicial los resuelve el servicio (segun el servicio contratado y la maquina de estados).
 */
@Mapper(componentModel = "spring")
public interface ServiceRequestCommandMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "previousRequestId", ignore = true)
    @Mapping(target = "offererId", ignore = true) // TODO derivar del servicio
    @Mapping(target = "status", ignore = true) // TODO PENDING al crear
    @Mapping(target = "updatedBy", ignore = true)
    @Mapping(target = "requestedPrice", ignore = true) // TODO precio del servicio
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "completedAt", ignore = true)
    @Mapping(target = "updatedStatusAt", ignore = true)
    ServiceRequest toDomain(CreateServiceRequestCommand command);
}
