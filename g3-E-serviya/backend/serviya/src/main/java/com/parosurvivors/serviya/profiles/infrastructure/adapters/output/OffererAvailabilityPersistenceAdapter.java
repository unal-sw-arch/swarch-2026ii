package com.parosurvivors.serviya.profiles.infrastructure.adapters.output;

import com.parosurvivors.serviya.profiles.application.ports.output.OffererAvailabilityPersistencePort;
import com.parosurvivors.serviya.profiles.domain.OffererAvailability;
import com.parosurvivors.serviya.profiles.infrastructure.entities.OffererAvailabilityEntity;
import com.parosurvivors.serviya.profiles.infrastructure.mappers.OffererAvailabilityPersistenceMapper;
import com.parosurvivors.serviya.profiles.infrastructure.repositories.OffererAvailabilityRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class OffererAvailabilityPersistenceAdapter implements OffererAvailabilityPersistencePort {

    private final OffererAvailabilityRepository repository;
    private final OffererAvailabilityPersistenceMapper mapper;

    @Override
    public OffererAvailability save(OffererAvailability availability) {
        OffererAvailabilityEntity saved = repository.save(mapper.toEntity(availability));
        return mapper.toDomain(saved);
    }

    @Override
    public OffererAvailability update(OffererAvailability availability) {
        OffererAvailabilityEntity updated = repository.save(mapper.toEntity(availability));
        return mapper.toDomain(updated);
    }

    @Override
    public List<OffererAvailability> saveAll(List<OffererAvailability> availabilities) {
        List<OffererAvailabilityEntity> entities = availabilities.stream()
                .map(mapper::toEntity)
                .collect(Collectors.toList());
        return repository.saveAll(entities).stream()
                .map(mapper::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public Optional<OffererAvailability> findById(Long id) {
        return repository.findById(id).map(mapper::toDomain);
    }

    @Override
    public List<OffererAvailability> findByOffererId(Long offererId) {
        return repository.findByOffererId(offererId).stream()
                .map(mapper::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public void deleteById(Long id) {
        repository.deleteById(id);
    }

    @Override
    public void deleteByOffererId(Long offererId) {
        repository.deleteByOffererId(offererId);
    }
}
