package com.parosurvivors.serviya.services.application.ports.output;

import com.parosurvivors.serviya.services.domain.ServiceAvailability;

import java.util.List;

public interface ServiceAvailabilityPersistencePort {

    ServiceAvailability save(ServiceAvailability availability);
    void deleteById(Long id);
    ServiceAvailability update(ServiceAvailability availability);
    List<ServiceAvailability> findByServiceId(long serviceId);
    ServiceAvailability findById(Long id);
    
}
