package com.parosurvivors.serviya.services.application.ports.input;

import com.parosurvivors.serviya.services.application.dto.command.CreateServiceAvailabilityCommand;
import com.parosurvivors.serviya.services.application.dto.command.UpdateServiceAvailabilityCommand;
import com.parosurvivors.serviya.services.domain.ServiceAvailability;

import java.util.List;

public interface ServiceAvailabilityServicePort {

    ServiceAvailability create(CreateServiceAvailabilityCommand command);
    void delete(Long id);
    ServiceAvailability update(UpdateServiceAvailabilityCommand command);
    List<ServiceAvailability> getByServiceId(long serviceId);
    void applyGeneralTemplate(Long serviceId, Long offererId);
}
