package com.parosurvivors.serviya.services.infrastructure.adapters.output;

import com.parosurvivors.serviya.services.application.ports.output.ServicePersistencePort;
import com.parosurvivors.serviya.services.domain.Service;
import com.parosurvivors.serviya.services.infrastructure.entities.ServiceEntity;
import com.parosurvivors.serviya.services.infrastructure.mappers.ServicePersistenceMapper;
import com.parosurvivors.serviya.services.infrastructure.repositories.ServiceRepository;
import com.parosurvivors.serviya.services.infrastructure.repositories.ServiceSpecificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class ServicePersistenceAdapter implements ServicePersistencePort {

    private final ServiceRepository repository;
    private final ServicePersistenceMapper mapper;

    @Override
    public Service save(Service service) {
        ServiceEntity entity = mapper.toEntity(service);
        ServiceEntity saved = repository.save(entity);
        return mapper.toDomain(saved);
    }

    @Override
    public Optional<Service> findById(Long id) {
        return repository.findById(id)
                .map(mapper::toDomain);
    }

    @Override
    public List<Service> findAll() {
        return repository.findAll().stream()
                .map(mapper::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public List<Service> findByOffererId(Long offererId) {
        return repository.findByOffererId(offererId).stream()
                .map(mapper::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public Page<Service> search(com.parosurvivors.serviya.services.application.dto.query.SearchServiceQuery criteria,
                                Pageable pageable) {
        return repository
                .findAll(ServiceSpecificationRepository.fromQuery(criteria), pageable)
                .map(mapper::toDomain);
    }
    
    @Override
    public void deleteById(Long id) {
        repository.deleteById(id);
    }

    @Override
    public Service update(Service service) {
        ServiceEntity entity = mapper.toEntity(service);
        ServiceEntity updated = repository.save(entity);
        return mapper.toDomain(updated);
    }
}
