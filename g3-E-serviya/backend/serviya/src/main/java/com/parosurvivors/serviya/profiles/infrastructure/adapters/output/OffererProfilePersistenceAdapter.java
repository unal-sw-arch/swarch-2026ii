package com.parosurvivors.serviya.profiles.infrastructure.adapters.output;

import com.parosurvivors.serviya.profiles.application.ports.output.OffererProfilePersistencePort;
import com.parosurvivors.serviya.profiles.domain.OffererProfile;
import com.parosurvivors.serviya.profiles.infrastructure.entities.OffererProfileEntity;
import com.parosurvivors.serviya.profiles.infrastructure.mappers.OffererProfilePersistenceMapper;
import com.parosurvivors.serviya.profiles.infrastructure.repositories.OffererProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class OffererProfilePersistenceAdapter implements OffererProfilePersistencePort {

    private final OffererProfileRepository repository;
    private final OffererProfilePersistenceMapper mapper;

    @Override
    public OffererProfile save(OffererProfile profile) {
        OffererProfileEntity saved = repository.save(mapper.toEntity(profile));
        return mapper.toDomain(saved);
    }

    @Override
    public OffererProfile update(OffererProfile profile) {
        OffererProfileEntity updated = repository.save(mapper.toEntity(profile));
        return mapper.toDomain(updated);
    }

    @Override
    public Optional<OffererProfile> findById(Long id) {
        return repository.findById(id).map(mapper::toDomain);
    }

    @Override
    public Optional<OffererProfile> findByUserId(Long userId) {
        return repository.findByUserId(userId).map(mapper::toDomain);
    }
}
