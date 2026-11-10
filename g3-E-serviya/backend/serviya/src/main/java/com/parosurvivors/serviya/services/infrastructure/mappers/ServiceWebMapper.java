package com.parosurvivors.serviya.services.infrastructure.mappers;

import com.parosurvivors.serviya.services.application.dto.command.CreateServiceCommand;
import com.parosurvivors.serviya.services.application.dto.command.UpdateServiceCommand;
import com.parosurvivors.serviya.services.domain.Service;
import com.parosurvivors.serviya.services.infrastructure.dto.form.CreateServiceForm;
import com.parosurvivors.serviya.services.infrastructure.dto.form.UpdateServiceForm;
import com.parosurvivors.serviya.services.infrastructure.dto.response.ServiceResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface ServiceWebMapper {

    @Mapping(target = "offererId", source = "offererId")
    CreateServiceCommand toCommand(CreateServiceForm form, Long offererId);

    @Mapping(target = "serviceId", source = "serviceId")
    UpdateServiceCommand toCommand(UpdateServiceForm form, Long serviceId);

    @Mapping(target = "offererName", ignore = true)
    @Mapping(target = "averageRating", ignore = true)
    @Mapping(target = "totalRatings", ignore = true)
    @Mapping(target = "totalComments", ignore = true)
    ServiceResponse toResponse(Service service);

    List<ServiceResponse> toResponses(List<Service> services);
}
