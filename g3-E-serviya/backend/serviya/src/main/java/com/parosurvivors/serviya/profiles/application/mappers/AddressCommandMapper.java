package com.parosurvivors.serviya.profiles.application.mappers;

import com.parosurvivors.serviya.profiles.application.dto.command.CreateAddressCommand;
import com.parosurvivors.serviya.profiles.application.dto.command.UpdateAddressCommand;
import com.parosurvivors.serviya.profiles.domain.Address;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

/**
 * Mapper de aplicacion (MapStruct) Command -> dominio de direcciones. Capa de aplicacion (no depende de
 * infraestructura). PLACEHOLDER: lo usara AddressService cuando se implemente la logica.
 */
@Mapper(componentModel = "spring")
public interface AddressCommandMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    Address toDomain(CreateAddressCommand command);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "userId", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    void updateFromCommand(UpdateAddressCommand command, @MappingTarget Address address);
}
