package com.parosurvivors.serviya.profiles.infrastructure.adapters.output;

import com.parosurvivors.serviya.profiles.application.ports.output.UserProfilePersistencePort;
import com.parosurvivors.serviya.profiles.domain.UserProfile;
import com.parosurvivors.serviya.profiles.infrastructure.entities.UserProfileEntity;
import com.parosurvivors.serviya.profiles.infrastructure.mappers.UserProfilePersistenceMapper;
import com.parosurvivors.serviya.profiles.infrastructure.repositories.UserProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class UserProfilePersistenceAdapter implements UserProfilePersistencePort {

    private final UserProfileRepository repository;
    private final UserProfilePersistenceMapper mapper;

    @Override
    public UserProfile save(UserProfile profile) {
        UserProfileEntity saved = repository.save(mapper.toEntity(profile));
        return mapper.toDomain(saved);
    }

    @Override
    public UserProfile update(UserProfile profile) {
        UserProfileEntity updated = repository.save(mapper.toEntity(profile));
        return mapper.toDomain(updated);
    }

    @Override
    public Optional<UserProfile> findById(Long id) {
        return repository.findById(id).map(mapper::toDomain);
    }

    @Override
    public Optional<UserProfile> findByUserId(Long userId) {
        return repository.findByUserId(userId).map(mapper::toDomain);
    }
}
