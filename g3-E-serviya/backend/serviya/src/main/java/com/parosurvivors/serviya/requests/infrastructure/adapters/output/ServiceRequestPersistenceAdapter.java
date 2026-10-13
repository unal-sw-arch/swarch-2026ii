package com.parosurvivors.serviya.requests.infrastructure.adapters.output;

import com.parosurvivors.serviya.requests.application.ports.output.ServiceRequestPersistencePort;
import com.parosurvivors.serviya.requests.domain.ServiceRequest;
import com.parosurvivors.serviya.requests.infrastructure.entities.ServiceRequestEntity;
import com.parosurvivors.serviya.requests.infrastructure.mappers.ServiceRequestPersistenceMapper;
import com.parosurvivors.serviya.requests.infrastructure.repositories.ServiceRequestRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Adapter de PERSISTENCIA (mutaciones) de solicitudes de servicio: solo save/update. Todas las
 * lecturas viven en {@code ServiceRequestReadAdapter} ({@code ServiceRequestReadPort}).
 */
@Component
@RequiredArgsConstructor
public class ServiceRequestPersistenceAdapter implements ServiceRequestPersistencePort {

    private final ServiceRequestRepository repository;
    private final ServiceRequestPersistenceMapper mapper;

    @Override
    public ServiceRequest save(ServiceRequest request) {
        ServiceRequestEntity saved = repository.save(mapper.toEntity(request));
        return mapper.toDomain(saved);
    }

    @Override
    public ServiceRequest update(ServiceRequest request) {
        ServiceRequestEntity updated = repository.save(mapper.toEntity(request));
        return mapper.toDomain(updated);
    }
}
