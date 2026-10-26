package com.parosurvivors.serviya.requests.application.mappers;

import com.parosurvivors.serviya.requests.application.dto.command.CreateRescheduleProposalCommand;
import com.parosurvivors.serviya.requests.domain.RescheduleProposal;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * Mapper de aplicacion (MapStruct) Command -> dominio de propuestas de reprogramacion. Capa de aplicacion.
 * PLACEHOLDER: lo usara RescheduleProposalService.createProposal. El estado inicial (PENDING) y los
 * timestamps los fija el servicio.
 */
@Mapper(componentModel = "spring")
public interface RescheduleProposalCommandMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "status", ignore = true) // TODO PENDING al crear
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "respondedAt", ignore = true)
    // Partes denormalizadas: las fija el servicio desde la solicitud, no vienen del command.
    @Mapping(target = "clientId", ignore = true)
    @Mapping(target = "offererId", ignore = true)
    RescheduleProposal toDomain(CreateRescheduleProposalCommand command);
}
