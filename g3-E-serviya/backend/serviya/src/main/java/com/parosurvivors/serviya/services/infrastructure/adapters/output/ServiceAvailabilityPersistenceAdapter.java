package com.parosurvivors.serviya.services.infrastructure.adapters.output;

import com.parosurvivors.serviya.services.application.ports.output.ServiceAvailabilityPersistencePort;
import com.parosurvivors.serviya.services.domain.ServiceAvailability;
import com.parosurvivors.serviya.services.infrastructure.mappers.ServiceAvailabilityPersistenceMapper;
import com.parosurvivors.serviya.services.infrastructure.repositories.ServiceAvailabilityRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class ServiceAvailabilityPersistenceAdapter implements ServiceAvailabilityPersistencePort {

    private final ServiceAvailabilityRepository repository;
    private final ServiceAvailabilityPersistenceMapper mapper;

    @Override
    public ServiceAvailability save(ServiceAvailability availability) {
        return mapper.toDomain(repository.save(mapper.toEntity(availability)));
    }

    @Override
    public void deleteById(Long id) {
        repository.deleteById(id);
    }

    @Override
    public ServiceAvailability update(ServiceAvailability availability) {
        return mapper.toDomain(repository.save(mapper.toEntity(availability)));
    }

    @Override
    public List<ServiceAvailability> findByServiceId(long serviceId) {
        return repository.findByServiceId(serviceId).stream()
                .map(mapper::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public ServiceAvailability findById(Long id) {
        return repository.findById(id)
                .map(mapper::toDomain)
                .orElseThrow(() -> new IllegalArgumentException("Disponibilidad no encontrada"));
    }
}
