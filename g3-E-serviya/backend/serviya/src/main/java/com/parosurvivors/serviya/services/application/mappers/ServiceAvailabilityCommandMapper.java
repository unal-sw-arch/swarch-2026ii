package com.parosurvivors.serviya.services.application.mappers;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.parosurvivors.serviya.services.application.dto.command.CreateServiceAvailabilityCommand;
import com.parosurvivors.serviya.services.application.dto.command.UpdateServiceAvailabilityCommand;
import com.parosurvivors.serviya.services.domain.ServiceAvailability;

@Mapper(componentModel = "spring")
public interface ServiceAvailabilityCommandMapper {

    @Mapping(target = "id", ignore = true)
    ServiceAvailability toDomain(CreateServiceAvailabilityCommand command);
    
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "serviceId", ignore = true)
    @Mapping(target = "weekDay", ignore = true)
    @Mapping(target = "startTime", ignore = true)
    @Mapping(target = "endTime", ignore = true)
    @Mapping(target = "isActive", ignore = true)
    ServiceAvailability updateFromCommand(UpdateServiceAvailabilityCommand command, ServiceAvailability serviceAvailability);
}
