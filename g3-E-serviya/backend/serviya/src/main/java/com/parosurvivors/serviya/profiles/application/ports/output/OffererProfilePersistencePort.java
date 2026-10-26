package com.parosurvivors.serviya.profiles.application.ports.output;

import com.parosurvivors.serviya.profiles.domain.OffererProfile;

import java.util.Optional;

public interface OffererProfilePersistencePort {
    OffererProfile save(OffererProfile profile);
    OffererProfile update(OffererProfile profile);
    Optional<OffererProfile> findById(Long id);
    Optional<OffererProfile> findByUserId(Long userId);
}
