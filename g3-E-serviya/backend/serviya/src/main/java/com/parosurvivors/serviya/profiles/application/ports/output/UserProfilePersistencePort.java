package com.parosurvivors.serviya.profiles.application.ports.output;

import com.parosurvivors.serviya.profiles.domain.UserProfile;

import java.util.Optional;

public interface UserProfilePersistencePort {
    UserProfile save(UserProfile profile);
    UserProfile update(UserProfile profile);
    Optional<UserProfile> findById(Long id);
    Optional<UserProfile> findByUserId(Long userId);
}
