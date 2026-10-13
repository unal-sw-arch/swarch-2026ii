package com.parosurvivors.serviya.services.infrastructure.mappers;

import com.parosurvivors.serviya.services.infrastructure.dto.form.CreateServiceAvailabilityForm;
import com.parosurvivors.serviya.services.infrastructure.dto.form.UpdateServiceAvailabilityForm;
import com.parosurvivors.serviya.services.infrastructure.dto.response.ServiceAvailabilityResponse;
import com.parosurvivors.serviya.services.application.dto.command.UpdateServiceAvailabilityCommand;
import com.parosurvivors.serviya.services.domain.ServiceAvailability;

import java.util.List;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.parosurvivors.serviya.services.application.dto.command.CreateServiceAvailabilityCommand;

@Mapper(componentModel = "spring")
public interface ServiceAvailabilityWebMapper {

    @Mapping(target = "activeStatus", source = "active")
    ServiceAvailabilityResponse toResponse(ServiceAvailability serviceAvailability);

    List<ServiceAvailabilityResponse> toResponses(List<ServiceAvailability> serviceAvailabilities);

    @Mapping(target = "serviceId", source = "serviceId")
    @Mapping(target = "weekDay", source = "form.weekDay")
    @Mapping(target = "startTime", source = "form.startTime")
    @Mapping(target = "endTime", source = "form.endTime")
    @Mapping(target = "isActive", source = "form.isActive", defaultValue = "true")
    CreateServiceAvailabilityCommand toCommand(CreateServiceAvailabilityForm form, Long serviceId);

    @Mapping(target = "id", source = "id")
    @Mapping(target = "weekDay", source = "form.weekDay")
    @Mapping(target = "startTime", source = "form.startTime")
    @Mapping(target = "endTime", source = "form.endTime")
    @Mapping(target = "isActive", source = "form.isActive", defaultValue = "true")
    UpdateServiceAvailabilityCommand toCommand(UpdateServiceAvailabilityForm form, Long id);
}
