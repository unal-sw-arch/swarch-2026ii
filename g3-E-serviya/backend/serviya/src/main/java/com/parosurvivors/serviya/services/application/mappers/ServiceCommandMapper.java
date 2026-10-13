package com.parosurvivors.serviya.services.application.mappers;

import com.parosurvivors.serviya.services.application.dto.command.CreateServiceCommand;
import com.parosurvivors.serviya.services.application.dto.command.UpdateServiceCommand;
import com.parosurvivors.serviya.services.domain.Service;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

/**
 * Mapper de aplicacion (MapStruct) Command -> dominio del feature services. Vive en la capa de
 * aplicacion (no depende de infraestructura): convierte los Commands en la entidad de dominio Service
 * sin que el servicio tenga que copiar campo a campo. Lo usa MarketplaceService.
 */
@Mapper(componentModel = "spring")
public interface ServiceCommandMapper {

    /** Crea un Service nuevo a partir del command. id/timestamps los gestiona el servicio; nace activo. */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "active", constant = "true")
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "deletedAt", ignore = true)
    Service toDomain(CreateServiceCommand command);

    /** Aplica los campos NO nulos del command sobre un Service ya cargado (PATCH semantico). */
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "offererId", ignore = true)
    @Mapping(target = "active", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "deletedAt", ignore = true)
    void updateFromCommand(UpdateServiceCommand command, @MappingTarget Service service);
}
